package com.nio.appstore.domain.upgrade

import com.nio.appstore.common.result.VersionUtils
import com.nio.appstore.core.logger.AppLogger
import com.nio.appstore.core.tracker.EventTracker
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.InstallStatus
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.state.UpgradeStatus
import com.nio.appstore.domain.text.BusinessText
import kotlinx.coroutines.delay

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
) : UpgradeManager {

    /** 检查当前应用是否存在可升级版本，并同步升级状态。 */
    override suspend fun checkUpgrade(appId: String): Boolean {
        require(appId.isNotBlank()) { "appId 不能为空" }
        val installedVersion = stateCenter.snapshot(appId).installedVersion ?: return false
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
    override suspend fun startBatchUpgrade(appIds: List<String>) {
        require(appIds.isNotEmpty()) { "升级列表不能为空" }
        for (appId in appIds) {
            require(appId.isNotBlank()) { "升级列表中的 appId 不能为空" }
            val policy = policyCenter.canUpgrade(appId)
            if (!policy.allow) {
                stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.upgradeRestricted(policy.reason))
                return
            }
            startUpgrade(appId)
            // 等待单个升级完成后再继续下一个。
            while (true) {
                delay(200)
                val state = stateCenter.snapshot(appId)
                when (state.upgradeStatus) {
                    UpgradeStatus.NONE -> break
                    UpgradeStatus.FAILED -> return
                    else -> Unit
                }
            }
        }
    }

    /**
     * 启动升级流程。
     *
     * 当前实现本质上是一个编排器：先下载，再安装，并通过轮询状态中心等待阶段完成。
     */
    override suspend fun startUpgrade(appId: String) {
        require(appId.isNotBlank()) { "appId 不能为空" }
        // 升级前先做策略判断，避免在不允许升级时继续消耗下载和安装资源。
        val policy = policyCenter.canUpgrade(appId)
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
        while (true) {
            delay(200)
            val state = stateCenter.snapshot(appId)
            when {
                state.downloadStatus == DownloadStatus.COMPLETED -> break
                state.downloadStatus == DownloadStatus.FAILED -> {
                    // 下载失败时直接结束升级流程，并把失败原因映射为升级失败。
                    stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_DOWNLOAD_FAILED)
                    return
                }
            }
        }

        // 第二阶段进入安装链路，等待最终安装结果决定升级是否成功。
        installManager.install(appId)
        while (true) {
            delay(200)
            val state = stateCenter.snapshot(appId)
            when (state.installStatus) {
                InstallStatus.INSTALLED -> {
                    // 安装成功后，升级任务就可以收口回到无待处理状态。
                    stateCenter.updateUpgrade(appId, UpgradeStatus.NONE)
                    tracker.track("upgrade_success_$appId")
                    return
                }
                InstallStatus.FAILED -> {
                    // 安装失败时，把升级状态统一折叠成失败态。
                    stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_INSTALL_FAILED)
                    return
                }
                else -> Unit
            }
        }
    }
}
