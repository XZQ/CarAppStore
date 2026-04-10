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
    /** 统一数据入口，负责读取应用列表、详情和任务记录。 */
    private val repository: AppRepository,
    /** 全局运行态状态中心，负责提供每个应用的实时状态快照。 */
    private val stateCenter: StateCenter,
    /** 安装会话存储，供安装中心读取最新会话状态。 */
    private val installSessionStore: InstallSessionStore,
) : AppManager {

    /** 获取首页应用卡片列表，并补齐已安装版本与升级状态。 */
    override suspend fun getHomeApps(): List<AppViewData> {
        val apps = repository.getHomeApps()
        val installed = repository.getInstalledApps().associateBy { it.appId }
        // 先把已安装版本同步进状态中心，后续构建卡片时才能得到正确主按钮。
        installed.forEach { (appId, app) -> stateCenter.syncInstalled(appId, app.versionName) }

        return apps.mapNotNull { app ->
            // 首页卡片构建前先同步升级可用性，保证展示的主动作和状态文案一致。
            syncUpgradeAvailability(app.appId, installed[app.appId]?.versionName)
            buildViewData(app.appId, app.name, app.description, app.versionName, app.packageName)
        }
    }

    /** 获取指定应用详情，并在已安装情况下补齐版本和升级状态。 */
    override suspend fun getAppDetail(appId: String): AppDetail {
        val detail = repository.getAppDetail(appId)
        if (repository.isInstalled(appId)) {
            val installedVersion = repository.getInstalledApps().firstOrNull { it.appId == appId }?.versionName ?: detail.versionName
            stateCenter.syncInstalled(appId, installedVersion)
            syncUpgradeAvailability(appId, installedVersion)
        }
        return detail
    }

    /** 获取“我的应用”页面需要展示的应用列表。 */
    override suspend fun getMyApps(): List<AppViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val installedApps = repository.getInstalledApps()
        // “我的应用”以已安装应用为主，同时补充有进行中任务的应用。
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

    /** 获取首页中单个应用的聚合视图数据。 */
    override suspend fun getHomeAppViewData(appId: String): AppViewData? {
        val app = repository.getHomeApps().firstOrNull { it.appId == appId } ?: return null
        val installedVersion = repository.getInstalledApps().firstOrNull { it.appId == appId }?.versionName
        if (installedVersion != null) stateCenter.syncInstalled(appId, installedVersion)
        syncUpgradeAvailability(appId, installedVersion)
        return buildViewData(app.appId, app.name, app.description, app.versionName, app.packageName)
    }

    /** 根据关键字搜索应用卡片。 */
    override suspend fun searchApps(keyword: String): List<AppViewData> {
        val normalized = keyword.trim()
        val source = getHomeApps()
        if (normalized.isBlank()) return source
        return source.filter {
            it.name.contains(normalized, ignoreCase = true) || it.description.contains(normalized, ignoreCase = true)
        }
    }

    /** 获取下载管理页顶部应用卡片集合。 */
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

    /** 获取下载任务中心需要展示的下载任务列表。 */
    override suspend fun getDownloadTasks(): List<DownloadTaskViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val tasks = repository.getAllDownloadTasks()
        return tasks.mapNotNull { task ->
            val home = homeApps[task.appId]
            val state = stateCenter.snapshot(task.appId)
            val file = File(task.targetFilePath)
            val actualSize = if (file.exists()) file.length() else task.downloadedBytes
            // 二级操作文案由任务状态决定，避免页面自己判断删除/取消/清理 APK。
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

    /** 获取安装中心需要展示的安装任务列表。 */
    override suspend fun getInstallTasks(): List<InstallTaskViewData> {
        val homeApps = repository.getHomeApps().associateBy { it.appId }
        val installedApps = repository.getInstalledApps().associateBy { it.appId }
        val sessionsByAppId = stateCenter.observeAll().value.keys
            .associateWith { appId -> installSessionStore.getLatestByAppId(appId) }

        return stateCenter.observeAll().value.values.mapNotNull { state ->
            // 只有等待安装、待确认、安装中和安装失败态才需要进入安装中心。
            val relevant = state.installStatus == InstallStatus.WAITING ||
                state.installStatus == InstallStatus.PENDING_USER_ACTION ||
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

    /** 将安装会话状态映射为安装中心使用的会话分桶。 */
    private fun mapSessionBucket(status: String?): SessionBucket {
        return when {
            status.isNullOrBlank() -> SessionBucket.NONE
            status == InstallSessionStatus.RECOVERED_INTERRUPTED -> SessionBucket.RECOVERED
            InstallSessionStatus.isFailed(status) -> SessionBucket.FAILED
            status == InstallSessionStatus.CALLBACK_SUCCESS -> SessionBucket.COMPLETED
            status == InstallSessionStatus.COMMITTED ||
                status == InstallSessionStatus.CREATED ||
                status == InstallSessionStatus.WRITTEN ||
                status == InstallSessionStatus.PENDING_USER_ACTION -> SessionBucket.ACTIVE
            else -> SessionBucket.NONE
        }
    }

    /** 合并应用级失败信息和安装会话失败信息。 */
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

    /** 获取升级管理页顶部应用卡片集合。 */
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

    /** 获取升级中心需要展示的升级任务列表。 */
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
            // 只有可升级、升级中和升级失败态才进入升级中心。
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

    /** 计算下载中心统计信息。 */
    override suspend fun getDownloadTaskStats(): TaskCenterStats = buildTaskStats(getDownloadTasks().map { it.overallStatus })

    /** 计算安装中心统计信息。 */
    override suspend fun getInstallTaskStats(): TaskCenterStats = buildTaskStats(getInstallTasks().map { it.overallStatus })

    /** 计算升级中心统计信息。 */
    override suspend fun getUpgradeTaskStats(): TaskCenterStats = buildTaskStats(getUpgradeTasks().map { it.overallStatus })

    /** 聚合当前策略提示文案。 */
    override fun getPolicyPrompt(): String {
        val settings = repository.getPolicySettings()
        val prompts = mutableListOf<String>()
        if (!settings.wifiConnected) prompts += BusinessText.POLICY_DOWNLOAD_CELLULAR
        if (!settings.parkingMode) prompts += BusinessText.POLICY_INSTALL_DRIVING
        if (settings.lowStorageMode) prompts += BusinessText.POLICY_STORAGE_LIMITED
        return if (prompts.isEmpty()) BusinessText.POLICY_ALL_CLEAR else prompts.joinToString("；")
    }

    /** 尝试打开指定包名的应用。 */
    override fun openApp(packageName: String): Boolean = repository.openApp(packageName)

    /** 根据当前安装版本同步升级可用性。 */
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

    /** 将应用基础数据和运行态合成为页面卡片模型。 */
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

    /** 判断一个应用是否应该出现在“我的应用”列表中。 */
    private fun shouldShowInMyApps(state: AppState): Boolean {
        return state.installStatus == InstallStatus.INSTALLED ||
            state.downloadStatus != DownloadStatus.IDLE ||
            state.installStatus == InstallStatus.WAITING ||
            state.installStatus == InstallStatus.PENDING_USER_ACTION ||
            state.installStatus == InstallStatus.FAILED ||
            state.upgradeStatus == UpgradeStatus.AVAILABLE ||
            state.upgradeStatus == UpgradeStatus.UPGRADING
    }

    /** 判断某个主操作是否应该在列表排序时前置。 */
    private fun shouldSortFirst(action: PrimaryAction): Boolean {
        return action == PrimaryAction.UPGRADE || action == PrimaryAction.INSTALL || action == PrimaryAction.PAUSE
    }

    /** 对下载任务做桶排序，让更需要用户处理的任务排前面。 */
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

    /** 对升级任务做桶排序，让可升级和进行中任务优先展示。 */
    private fun upgradeTaskBucket(item: UpgradeTaskViewData): Int {
        return when (item.primaryAction) {
            PrimaryAction.UPGRADE -> 0
            PrimaryAction.PAUSE, PrimaryAction.RESUME -> 1
            PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> 2
            PrimaryAction.OPEN -> 3
            else -> 4
        }
    }

    /** 将字节数格式化为人类可读文本。 */
    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        return when {
            bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    /** 将下载速度格式化为人类可读文本。 */
    private fun formatSpeed(bytesPerSec: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        return when {
            bytesPerSec >= mb -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / mb)
            bytesPerSec >= kb -> String.format(Locale.getDefault(), "%.0f KB/s", bytesPerSec / kb)
            else -> "$bytesPerSec B/s"
        }
    }

    /** 生成任务更新时间和重试次数描述。 */
    private fun buildTaskTimeText(updatedAt: Long, retryCount: Int): String {
        val retryPart = if (retryCount > 0) BusinessText.retryPart(retryCount) else ""
        return BusinessText.updatedAt(formatTime(updatedAt)) + retryPart
    }

    /** 组合失败码和失败文案。 */
    private fun buildReasonText(failureCode: String?, failureMessage: String?): String? {
        if (failureMessage.isNullOrBlank() && failureCode.isNullOrBlank()) return null
        return listOfNotNull(
            failureCode?.takeIf { it.isNotBlank() },
            failureMessage?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    /** 将时间戳格式化为列表展示文本。 */
    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    /** 将下载状态映射为任务中心总状态。 */
    private fun mapDownloadOverallStatus(status: DownloadStatus): TaskOverallStatus = when (status) {
        DownloadStatus.WAITING, DownloadStatus.PAUSED -> TaskOverallStatus.PENDING
        DownloadStatus.RUNNING -> TaskOverallStatus.ACTIVE
        DownloadStatus.FAILED, DownloadStatus.CANCELED -> TaskOverallStatus.FAILED
        DownloadStatus.COMPLETED -> TaskOverallStatus.COMPLETED
        DownloadStatus.IDLE -> TaskOverallStatus.PENDING
    }

    /** 将安装状态映射为任务中心总状态。 */
    private fun mapInstallOverallStatus(state: AppState): TaskOverallStatus = when (state.installStatus) {
        InstallStatus.WAITING -> TaskOverallStatus.PENDING
        InstallStatus.PENDING_USER_ACTION -> TaskOverallStatus.PENDING
        InstallStatus.INSTALLING -> TaskOverallStatus.ACTIVE
        InstallStatus.FAILED -> TaskOverallStatus.FAILED
        InstallStatus.INSTALLED -> TaskOverallStatus.COMPLETED
        InstallStatus.NOT_INSTALLED -> TaskOverallStatus.PENDING
    }

    /** 将升级状态映射为任务中心总状态。 */
    private fun mapUpgradeOverallStatus(state: AppState): TaskOverallStatus = when (state.upgradeStatus) {
        UpgradeStatus.AVAILABLE -> TaskOverallStatus.PENDING
        UpgradeStatus.UPGRADING -> TaskOverallStatus.ACTIVE
        UpgradeStatus.FAILED -> TaskOverallStatus.FAILED
        UpgradeStatus.SUCCESS -> TaskOverallStatus.COMPLETED
        UpgradeStatus.NONE -> TaskOverallStatus.PENDING
    }

    /** 根据总状态集合构建任务中心统计信息。 */
    private fun buildTaskStats(statuses: List<TaskOverallStatus>): TaskCenterStats = TaskCenterStats(
        activeCount = statuses.count { it == TaskOverallStatus.ACTIVE },
        pendingCount = statuses.count { it == TaskOverallStatus.PENDING },
        failedCount = statuses.count { it == TaskOverallStatus.FAILED },
        completedCount = statuses.count { it == TaskOverallStatus.COMPLETED },
    )

}
