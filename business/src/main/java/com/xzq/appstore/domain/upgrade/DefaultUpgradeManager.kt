package com.xzq.appstore.domain.upgrade

import com.xzq.appstore.common.result.VersionUtils
import com.xzq.appstore.core.logger.AppLogger
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.data.model.ClientPlatformCapabilities
import com.xzq.appstore.data.repository.AppRepository
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.state.DownloadStatus
import com.xzq.appstore.domain.state.InstallStatus
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.state.UpgradeStatus
import com.xzq.appstore.domain.text.BusinessText
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class DefaultUpgradeManager(
    /** 统一数据入口，负责升级信息和 staged version 读写。 */
    private val repository: AppRepository,
    /** 升级运行态状态中心。 */
    private val stateCenter: StateCenter,
    /** 升级前置策略中心。 */
    private val policyCenter: PolicyCenter,
    /** 升级下载阶段使用的下载编排器。 */
    private val downloadManager: DownloadManager,
    /** 升级安装阶段使用的安装编排器。 */
    private val installManager: InstallManager,
    /** 升级链路日志器。 */
    private val logger: AppLogger,
    /** 升级链路打点器。 */
    private val tracker: EventTracker,
    /** 轮询下载与安装终态的间隔；生产默认保持 200ms。 */
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    /** 单个升级阶段的最大等待时间；生产默认 30 分钟。 */
    private val waitTimeoutMs: Long = WAIT_TIMEOUT_MS,
    /** 当前客户端平台能力，用于阻止升级到其他平台安装包。 */
    private val platformCapabilities: ClientPlatformCapabilities = ClientPlatformCapabilities(),
) : UpgradeManager {
    /** 检查当前应用是否存在可升级版本，并同步升级状态。 */
    override suspend fun checkUpgrade(appId: String): Boolean {
        require(appId.isNotBlank()) { "appId 不能为空" }
        val installedVersion = stateCenter.snapshot(appId).installedVersion ?: return false
        if (!isCurrentPlatformSupported(appId)) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.NONE)
            return false
        }
        val info = repository.getUpgradeInfo(appId)
        val available = info.hasUpgrade && VersionUtils.isNewerVersion(installedVersion, info.latestVersion)
        stateCenter.updateUpgrade(appId, if (available) UpgradeStatus.AVAILABLE else UpgradeStatus.NONE)
        return available
    }

    /** 检查全部已安装应用是否有可升级版本，返回有升级的 appId 列表。 */
    override suspend fun checkAllUpgrades(): List<String> {
        val installed = repository.getInstalledApps()
        return installed.mapNotNull { app ->
            val hasUpgrade = checkUpgrade(app.appId)
            if (hasUpgrade) app.appId else null
        }
    }

    /** 批量启动升级流程，逐个串行执行，遇到失败时停止后续。 */
    override suspend fun startBatchUpgrade(appIds: List<String>): UpgradeBatchResult {
        require(appIds.isNotEmpty()) { "升级列表不能为空" }
        val succeeded = mutableListOf<String>()
        val failed = mutableMapOf<String, String>()
        val skipped = mutableMapOf<String, String>()
        for (appId in appIds) {
            require(appId.isNotBlank()) { "升级列表中的 appId 不能为空" }
            if (!isCurrentPlatformSupported(appId)) {
                val reason = BusinessText.STATUS_PLATFORM_UNSUPPORTED
                stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = reason)
                skipped[appId] = reason
                return@startBatchUpgrade UpgradeBatchResult(succeeded = succeeded, failed = failed, skipped = skipped)
            }
            // 与单任务升级一致：APK 已落盘时不再要求下载链路条件。
            val policy = policyCenter.canUpgrade(appId, isApkCached(appId))
            if (!policy.allow) {
                val reason = BusinessText.upgradeRestricted(policy.reason)
                stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = reason)
                skipped[appId] = reason
                return@startBatchUpgrade UpgradeBatchResult(succeeded = succeeded, failed = failed, skipped = skipped)
            }
            startUpgrade(appId)
            val state = stateCenter.snapshot(appId)
            if (state.upgradeStatus == UpgradeStatus.NONE) {
                succeeded.add(appId)
            } else {
                failed[appId] = state.errorMessage ?: BusinessText.UPGRADE_INSTALL_FAILED
                return@startBatchUpgrade UpgradeBatchResult(succeeded = succeeded, failed = failed, skipped = skipped)
            }
        }
        return UpgradeBatchResult(succeeded = succeeded, failed = failed, skipped = skipped)
    }

    /**
     * 启动升级流程。
     *
     * 当前实现本质上是一个编排器：先下载，再安装，并通过轮询状态中心等待阶段完成。
     */
    override suspend fun startUpgrade(appId: String) {
        require(appId.isNotBlank()) { "appId 不能为空" }
        if (!isCurrentPlatformSupported(appId)) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.STATUS_PLATFORM_UNSUPPORTED)
            return
        }
        // 升级前先做策略判断，APK 已落盘时跳过下载相关条件，避免 Wi-Fi 漂移等误拦已就绪任务。
        val apkAlreadyDownloaded = isApkCached(appId)
        val policy = policyCenter.canUpgrade(appId, apkAlreadyDownloaded)
        if (!policy.allow) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.upgradeRestricted(policy.reason))
            return
        }

        // 读取升级信息，并确认当前版本确实落后于目标版本。
        val upgradeInfo = repository.getUpgradeInfo(appId)
        val currentVersion = stateCenter.snapshot(appId).installedVersion
        if (!upgradeInfo.hasUpgrade || !VersionUtils.isNewerVersion(currentVersion, upgradeInfo.latestVersion)) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.NONE)
            return
        }

        logger.d("UpgradeManager", "startUpgrade: $appId to ${upgradeInfo.latestVersion}")
        tracker.track("upgrade_start_$appId")
        repository.stageUpgrade(appId, upgradeInfo.latestVersion)
        stateCenter.resetError(appId)
        stateCenter.updateUpgrade(appId, UpgradeStatus.UPGRADING)

        // 第一阶段先进入下载链路，成功后才有资格继续安装。
        downloadManager.startDownload(appId)
        when (awaitDownloadResult(appId)) {
            DownloadAwaitResult.COMPLETED -> Unit
            DownloadAwaitResult.FAILED -> {
                stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_DOWNLOAD_FAILED)
                return
            }

            DownloadAwaitResult.INTERRUPTED -> {
                stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_DOWNLOAD_INTERRUPTED)
                return
            }

            null -> {
                stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_DOWNLOAD_TIMEOUT)
                return
            }
        }

        // 第二阶段进入安装链路，等待最终安装结果决定升级是否成功。
        installManager.install(appId)
        when (awaitInstallResult(appId)) {
            InstallAwaitResult.INSTALLED -> {
                stateCenter.updateUpgrade(appId, UpgradeStatus.NONE)
                tracker.track("upgrade_success_$appId")
            }

            InstallAwaitResult.FAILED -> stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_INSTALL_FAILED)
            null -> stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_INSTALL_TIMEOUT)
        }
    }

    /** 等待下载阶段进入成功、失败或中断终态，超时返回 null。 */
    private suspend fun awaitDownloadResult(appId: String): DownloadAwaitResult? = withTimeoutOrNull(waitTimeoutMs) {
        while (true) {
            delay(pollIntervalMs)
            when (stateCenter.snapshot(appId).downloadStatus) {
                DownloadStatus.COMPLETED -> return@withTimeoutOrNull DownloadAwaitResult.COMPLETED
                DownloadStatus.FAILED -> return@withTimeoutOrNull DownloadAwaitResult.FAILED
                DownloadStatus.CANCELED, DownloadStatus.PAUSED -> return@withTimeoutOrNull DownloadAwaitResult.INTERRUPTED
                else -> Unit
            }
        }
        error("下载等待循环不应在未返回终态时结束")
    }

    /** 读取目录声明并判断当前客户端能否处理该应用安装包。 */
    private suspend fun isCurrentPlatformSupported(appId: String): Boolean {
        return platformCapabilities.supports(repository.getAppDetail(appId).supportedPlatforms)
    }

    /** 等待安装阶段进入终态，超时返回 null。 */
    private suspend fun awaitInstallResult(appId: String): InstallAwaitResult? = withTimeoutOrNull(waitTimeoutMs) {
        while (true) {
            delay(pollIntervalMs)
            when (stateCenter.snapshot(appId).installStatus) {
                InstallStatus.INSTALLED -> return@withTimeoutOrNull InstallAwaitResult.INSTALLED
                InstallStatus.FAILED -> return@withTimeoutOrNull InstallAwaitResult.FAILED
                else -> Unit
            }
        }
        error("安装等待循环不应在未返回终态时结束")
    }

    /** 判断指定应用的 APK 是否已落盘且可读，用于策略中心决定是否跳过下载链路校验。 */
    private suspend fun isApkCached(appId: String): Boolean = repository.getDownloadedApk(appId)?.takeIf { it.isNotBlank() && File(it).exists() } != null

    private enum class DownloadAwaitResult { COMPLETED, FAILED, INTERRUPTED }

    private enum class InstallAwaitResult { INSTALLED, FAILED }

    private companion object {
        const val POLL_INTERVAL_MS = 200L
        const val WAIT_TIMEOUT_MS = 30L * 60L * 1000L
    }
}
