package com.nio.appstore.domain.appmanager

import com.nio.appstore.common.result.VersionUtils
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.SessionBucket
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.TaskOverallStatus
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.core.installer.InstallSessionStatus
import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.domain.state.AppState
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.InstallStatus
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.state.UpgradeStatus
import com.nio.appstore.domain.text.BusinessText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DefaultAppManager(
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
    private val installSessionStore: InstallSessionStore,
) : AppManager {

    override suspend fun getHomeApps(): List<AppViewData> {
        val apps = repository.getHomeApps()
        val installed = repository.getInstalledApps().associateBy { it.appId }
        installed.forEach { (appId, app) -> stateCenter.syncInstalled(appId, app.versionName) }

        return apps.mapNotNull { app ->
            syncUpgradeAvailability(app.appId, installed[app.appId]?.versionName)
            buildViewData(app.appId, app.name, app.description, app.versionName, app.packageName)
        }
    }

    override suspend fun getAppDetail(appId: String): AppDetail {
        val detail = repository.getAppDetail(appId)
        if (repository.isInstalled(appId)) {
            val installedVersion = repository.getInstalledApps().firstOrNull { it.appId == appId }?.versionName ?: detail.versionName
            stateCenter.syncInstalled(appId, installedVersion)
            syncUpgradeAvailability(appId, installedVersion)
        }
        return detail
    }

    override suspend fun getMyApps(): List<AppViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val installedApps = repository.getInstalledApps()
        installedApps.forEach { installed ->
            stateCenter.syncInstalled(installed.appId, installed.versionName)
            syncUpgradeAvailability(installed.appId, installed.versionName)
        }
        val stateMap = stateCenter.observeAll().value
        val appIds = linkedSetOf<String>()
        installedApps.forEach { appIds.add(it.appId) }
        stateMap.forEach { (appId, state) -> if (shouldShowInMyApps(state)) appIds.add(appId) }
        return appIds.mapNotNull { appId ->
            val installed = installedApps.firstOrNull { it.appId == appId }
            val home = homeApps[appId]
            buildViewData(
                appId = appId,
                name = installed?.name ?: home?.name,
                description = if (installed != null) BusinessText.DESCRIPTION_INSTALLED_APP else home?.description ?: BusinessText.DESCRIPTION_APP_TASK,
                versionName = installed?.versionName ?: home?.versionName,
                packageName = installed?.packageName ?: home?.packageName,
            )
        }.sortedWith(compareByDescending<AppViewData> { shouldSortFirst(it.primaryAction) }.thenBy { it.name })
    }

    override suspend fun getHomeAppViewData(appId: String): AppViewData? {
        val app = repository.getHomeApps().firstOrNull { it.appId == appId } ?: return null
        val installedVersion = repository.getInstalledApps().firstOrNull { it.appId == appId }?.versionName
        if (installedVersion != null) stateCenter.syncInstalled(appId, installedVersion)
        syncUpgradeAvailability(appId, installedVersion)
        return buildViewData(app.appId, app.name, app.description, app.versionName, app.packageName)
    }

    override suspend fun searchApps(keyword: String): List<AppViewData> {
        val normalized = keyword.trim()
        val source = getHomeApps()
        if (normalized.isBlank()) return source
        return source.filter {
            it.name.contains(normalized, ignoreCase = true) || it.description.contains(normalized, ignoreCase = true)
        }
    }

    override suspend fun getDownloadManageApps(): List<AppViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val installedApps = repository.getInstalledApps().associateBy { it.appId }
        installedApps.forEach { (appId, app) -> stateCenter.syncInstalled(appId, app.versionName) }
        val stateMap = stateCenter.observeAll().value
        val appIds = stateMap.filterValues { state ->
            state.downloadStatus != DownloadStatus.IDLE || state.progress > 0 || state.localApkPath != null ||
                (state.installStatus == InstallStatus.WAITING) || (state.installStatus == InstallStatus.FAILED)
        }.keys

        return appIds.mapNotNull { appId ->
            val home = homeApps[appId]
            val installed = installedApps[appId]
            buildViewData(
                appId = appId,
                name = home?.name ?: installed?.name,
                description = home?.description ?: BusinessText.DESCRIPTION_DOWNLOAD_INSTALL_TASK,
                versionName = stateCenter.snapshot(appId).installedVersion ?: home?.versionName ?: installed?.versionName,
                packageName = home?.packageName ?: installed?.packageName,
            )
        }.sortedByDescending { it.progress }
    }

    override suspend fun getDownloadTasks(): List<DownloadTaskViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val tasks = repository.getAllDownloadTasks()
        return tasks.mapNotNull { task ->
            val home = homeApps[task.appId]
            val state = stateCenter.snapshot(task.appId)
            val file = File(task.targetFilePath)
            val actualSize = if (file.exists()) file.length() else task.downloadedBytes
            val secondaryActionText = when {
                task.status == DownloadStatus.COMPLETED -> BusinessText.ACTION_CLEAR_APK
                task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELED -> BusinessText.ACTION_DELETE_TASK
                else -> BusinessText.ACTION_CANCEL_TASK
            }
            DownloadTaskViewData(
                appId = task.appId,
                name = home?.name ?: task.appId,
                versionName = state.installedVersion ?: home?.versionName ?: "-",
                stateText = state.statusText,
                statusTone = CarUiStyle.resolveStatusTone(state),
                overallStatus = mapDownloadOverallStatus(task.status),
                progress = task.progress,
                primaryAction = state.primaryAction,
                sizeText = "${formatBytes(actualSize)} / ${formatBytes(task.totalBytes)}",
                speedText = if (task.status == DownloadStatus.RUNNING && task.speedBytesPerSec > 0L) formatSpeed(task.speedBytesPerSec) else "-",
                timeText = buildTaskTimeText(task.updatedAt, task.retryCount),
                pathText = if (task.targetFilePath.isBlank()) BusinessText.PATH_APK_NOT_READY else task.targetFilePath,
                secondaryActionText = secondaryActionText,
                showSecondaryAction = true,
                installed = state.installStatus == InstallStatus.INSTALLED,
                reasonText = buildReasonText(task.failureCode, state.errorMessage ?: task.failureMessage),
                updatedAt = task.updatedAt,
            )
        }.sortedWith(compareBy<DownloadTaskViewData> { downloadTaskBucket(it) }.thenByDescending { it.updatedAt })
    }


    override suspend fun getInstallTasks(): List<InstallTaskViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val installedApps = repository.getInstalledApps().associateBy { it.appId }
        val sessionsByAppId = stateCenter.observeAll().value.keys
            .associateWith { appId -> installSessionStore.getLatestByAppId(appId) }

        return stateCenter.observeAll().value.values.mapNotNull { state ->
            val relevant = state.installStatus == InstallStatus.WAITING ||
                state.installStatus == InstallStatus.INSTALLING ||
                state.installStatus == InstallStatus.FAILED
            if (!relevant) return@mapNotNull null
            val home = homeApps[state.appId]
            val installed = installedApps[state.appId]
            val session = sessionsByAppId[state.appId]
            InstallTaskViewData(
                appId = state.appId,
                packageName = home?.packageName ?: installed?.packageName.orEmpty(),
                name = home?.name ?: installed?.name ?: state.appId,
                versionName = state.installedVersion ?: installed?.versionName ?: home?.versionName ?: "-",
                stateText = state.statusText,
                statusTone = CarUiStyle.resolveStatusTone(state),
                overallStatus = mapInstallOverallStatus(state),
                primaryAction = state.primaryAction,
                reasonText = mergeInstallFailureText(
                    appErrorCode = state.errorCode,
                    appErrorMessage = state.errorMessage,
                    sessionFailureMessage = session?.failureMessage,
                    sessionStatus = session?.status,
                ),
                updatedAt = session?.updatedAt ?: System.currentTimeMillis(),
                sessionIdText = session?.sessionId?.takeIf { it >= 0 }?.let { "Session #$it" },
                sessionPhaseText = session?.status?.let(BusinessText::sessionPhase),
                sessionProgressText = session?.let { BusinessText.sessionProgress(it.progress) },
                sessionBucket = mapSessionBucket(session?.status),
            )
        }.sortedWith(compareBy<InstallTaskViewData> {
            when (it.primaryAction) {
                PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> 0
                PrimaryAction.DISABLED -> 1
                else -> 2
            }
        }.thenBy { it.name })
    }



    private fun mapSessionBucket(status: String?): SessionBucket {
        return when {
            status.isNullOrBlank() -> SessionBucket.NONE
            status == InstallSessionStatus.RECOVERED_INTERRUPTED -> SessionBucket.RECOVERED
            InstallSessionStatus.isFailed(status) -> SessionBucket.FAILED
            status == InstallSessionStatus.CALLBACK_SUCCESS || status == InstallSessionStatus.COMMITTED -> SessionBucket.COMPLETED
            status == InstallSessionStatus.CREATED || status == InstallSessionStatus.WRITTEN -> SessionBucket.ACTIVE
            else -> SessionBucket.NONE
        }
    }

    private fun mergeInstallFailureText(
        appErrorCode: String?,
        appErrorMessage: String?,
        sessionFailureMessage: String?,
        sessionStatus: String?,
    ): String? {
        val appText = buildReasonText(appErrorCode, appErrorMessage)
        val sessionText = when {
            !sessionFailureMessage.isNullOrBlank() -> sessionFailureMessage
            sessionStatus == InstallSessionStatus.RECOVERED_INTERRUPTED -> com.nio.appstore.core.installer.InstallerText.SESSION_INTERRUPTED_RECOVERABLE
            sessionStatus?.let { InstallSessionStatus.isFailed(it) } == true -> BusinessText.sessionFailed(sessionStatus)
            else -> null
        }
        return listOfNotNull(appText, sessionText)
            .distinct()
            .joinToString("；")
            .ifBlank { null }
    }

    override suspend fun getUpgradeManageApps(): List<AppViewData> {
        return getUpgradeTasks().map {
            AppViewData(
                appId = it.appId,
                name = it.name,
                description = BusinessText.upgradeTarget(it.currentVersion, it.targetVersion),
                versionName = it.currentVersion,
                packageName = it.packageName,
                stateText = it.stateText,
                statusTone = it.statusTone,
                primaryAction = it.primaryAction,
                progress = stateCenter.snapshot(it.appId).progress,
                installed = true,
            )
        }
    }

    override suspend fun getUpgradeTasks(): List<UpgradeTaskViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val installedApps = repository.getInstalledApps().associateBy { it.appId }
        installedApps.values.forEach { installed ->
            stateCenter.syncInstalled(installed.appId, installed.versionName)
            syncUpgradeAvailability(installed.appId, installed.versionName)
        }
        return installedApps.values.mapNotNull { installed ->
            val upgradeInfo = repository.getUpgradeInfo(installed.appId)
            val state = stateCenter.snapshot(installed.appId)
            val relevant = state.upgradeStatus == UpgradeStatus.AVAILABLE ||
                state.upgradeStatus == UpgradeStatus.UPGRADING ||
                state.installStatus == InstallStatus.INSTALLING ||
                state.installStatus == InstallStatus.FAILED ||
                state.upgradeStatus == UpgradeStatus.FAILED

            if (!relevant) return@mapNotNull null

            val home = homeApps[installed.appId]
            UpgradeTaskViewData(
                appId = installed.appId,
                packageName = installed.packageName,
                name = installed.name.ifBlank { home?.name ?: installed.appId },
                currentVersion = installed.versionName,
                targetVersion = upgradeInfo.latestVersion,
                stateText = state.statusText,
                statusTone = CarUiStyle.resolveStatusTone(state),
                overallStatus = mapUpgradeOverallStatus(state),
                primaryAction = state.primaryAction,
                reasonText = buildReasonText(state.errorCode, state.errorMessage),
                updatedAt = System.currentTimeMillis(),
            )
        }.sortedWith(compareBy<UpgradeTaskViewData> { upgradeTaskBucket(it) }.thenBy { it.name })
    }

    override suspend fun getDownloadTaskStats(): TaskCenterStats = buildTaskStats(getDownloadTasks().map { it.overallStatus })

    override suspend fun getInstallTaskStats(): TaskCenterStats = buildTaskStats(getInstallTasks().map { it.overallStatus })

    override suspend fun getUpgradeTaskStats(): TaskCenterStats = buildTaskStats(getUpgradeTasks().map { it.overallStatus })

    override fun getPolicyPrompt(): String {
        val settings = repository.getPolicySettings()
        val prompts = mutableListOf<String>()
        if (!settings.wifiConnected) prompts += BusinessText.POLICY_DOWNLOAD_CELLULAR
        if (!settings.parkingMode) prompts += BusinessText.POLICY_INSTALL_DRIVING
        if (settings.lowStorageMode) prompts += BusinessText.POLICY_STORAGE_LIMITED
        return if (prompts.isEmpty()) BusinessText.POLICY_ALL_CLEAR else prompts.joinToString("；")
    }

    override fun openApp(packageName: String): Boolean = repository.openApp(packageName)

    private suspend fun syncUpgradeAvailability(appId: String, installedVersion: String?) {
        if (installedVersion.isNullOrBlank()) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.NONE)
            return
        }
        val upgradeInfo = repository.getUpgradeInfo(appId)
        if (upgradeInfo.hasUpgrade && VersionUtils.isNewerVersion(installedVersion, upgradeInfo.latestVersion)) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.AVAILABLE)
        } else if (stateCenter.snapshot(appId).upgradeStatus != UpgradeStatus.UPGRADING) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.NONE)
        }
    }

    private fun buildViewData(appId: String, name: String?, description: String?, versionName: String?, packageName: String?): AppViewData? {
        if (name.isNullOrBlank() || versionName.isNullOrBlank()) return null
        val state = stateCenter.snapshot(appId)
        return AppViewData(
            appId = appId,
            name = name,
            description = description.orEmpty(),
            versionName = state.installedVersion ?: versionName,
            packageName = packageName.orEmpty(),
            stateText = state.statusText,
            progress = state.progress,
            primaryAction = state.primaryAction,
            statusTone = CarUiStyle.resolveStatusTone(state),
        )
    }

    private fun shouldShowInMyApps(state: AppState): Boolean {
        return state.installStatus == InstallStatus.INSTALLED ||
            state.downloadStatus != DownloadStatus.IDLE ||
            state.installStatus == InstallStatus.WAITING ||
            state.installStatus == InstallStatus.FAILED ||
            state.upgradeStatus == UpgradeStatus.AVAILABLE ||
            state.upgradeStatus == UpgradeStatus.UPGRADING
    }

    private fun shouldSortFirst(action: PrimaryAction): Boolean {
        return action == PrimaryAction.UPGRADE || action == PrimaryAction.INSTALL || action == PrimaryAction.PAUSE
    }

    private fun downloadTaskBucket(item: DownloadTaskViewData): Int {
        return when (item.primaryAction) {
            PrimaryAction.PAUSE -> 0
            PrimaryAction.RESUME -> 1
            PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> 2
            PrimaryAction.RETRY_DOWNLOAD -> 3
            PrimaryAction.OPEN -> 4
            else -> 5
        }
    }

    private fun upgradeTaskBucket(item: UpgradeTaskViewData): Int {
        return when (item.primaryAction) {
            PrimaryAction.UPGRADE -> 0
            PrimaryAction.PAUSE, PrimaryAction.RESUME -> 1
            PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> 2
            PrimaryAction.OPEN -> 3
            else -> 4
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        return when {
            bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        return when {
            bytesPerSec >= mb -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / mb)
            bytesPerSec >= kb -> String.format(Locale.getDefault(), "%.0f KB/s", bytesPerSec / kb)
            else -> "$bytesPerSec B/s"
        }
    }

    private fun buildTaskTimeText(updatedAt: Long, retryCount: Int): String {
        val retryPart = if (retryCount > 0) BusinessText.retryPart(retryCount) else ""
        return BusinessText.updatedAt(formatTime(updatedAt)) + retryPart
    }

    private fun buildReasonText(failureCode: String?, failureMessage: String?): String? {
        if (failureMessage.isNullOrBlank() && failureCode.isNullOrBlank()) return null
        return listOfNotNull(
            failureCode?.takeIf { it.isNotBlank() },
            failureMessage?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun mapDownloadOverallStatus(status: DownloadStatus): TaskOverallStatus = when (status) {
        DownloadStatus.WAITING, DownloadStatus.PAUSED -> TaskOverallStatus.PENDING
        DownloadStatus.RUNNING -> TaskOverallStatus.ACTIVE
        DownloadStatus.FAILED, DownloadStatus.CANCELED -> TaskOverallStatus.FAILED
        DownloadStatus.COMPLETED -> TaskOverallStatus.COMPLETED
        DownloadStatus.IDLE -> TaskOverallStatus.PENDING
    }

    private fun mapInstallOverallStatus(state: AppState): TaskOverallStatus = when (state.installStatus) {
        InstallStatus.WAITING -> TaskOverallStatus.PENDING
        InstallStatus.INSTALLING -> TaskOverallStatus.ACTIVE
        InstallStatus.FAILED -> TaskOverallStatus.FAILED
        InstallStatus.INSTALLED -> TaskOverallStatus.COMPLETED
        InstallStatus.NOT_INSTALLED -> TaskOverallStatus.PENDING
    }

    private fun mapUpgradeOverallStatus(state: AppState): TaskOverallStatus = when (state.upgradeStatus) {
        UpgradeStatus.AVAILABLE -> TaskOverallStatus.PENDING
        UpgradeStatus.UPGRADING -> TaskOverallStatus.ACTIVE
        UpgradeStatus.FAILED -> TaskOverallStatus.FAILED
        UpgradeStatus.SUCCESS -> TaskOverallStatus.COMPLETED
        UpgradeStatus.NONE -> TaskOverallStatus.PENDING
    }

    private fun buildTaskStats(statuses: List<TaskOverallStatus>): TaskCenterStats = TaskCenterStats(
        activeCount = statuses.count { it == TaskOverallStatus.ACTIVE },
        pendingCount = statuses.count { it == TaskOverallStatus.PENDING },
        failedCount = statuses.count { it == TaskOverallStatus.FAILED },
        completedCount = statuses.count { it == TaskOverallStatus.COMPLETED },
    )

}
