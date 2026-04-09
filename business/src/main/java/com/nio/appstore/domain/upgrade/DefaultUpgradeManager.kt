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
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
    private val policyCenter: PolicyCenter,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val logger: AppLogger,
    private val tracker: EventTracker,
) : UpgradeManager {

    override suspend fun checkUpgrade(appId: String): Boolean {
        val installedVersion = stateCenter.snapshot(appId).installedVersion ?: return false
        val info = repository.getUpgradeInfo(appId)
        val available = info.hasUpgrade && VersionUtils.isNewerVersion(installedVersion, info.latestVersion)
        stateCenter.updateUpgrade(appId, if (available) UpgradeStatus.AVAILABLE else UpgradeStatus.NONE)
        return available
    }

    override suspend fun startUpgrade(appId: String) {
        val policy = policyCenter.canUpgrade(appId)
        if (!policy.allow) {
            stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.upgradeRestricted(policy.reason))
            return
        }

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

        downloadManager.startDownload(appId)
        while (true) {
            delay(200)
            val state = stateCenter.snapshot(appId)
            when {
                state.downloadStatus == DownloadStatus.COMPLETED -> break
                state.downloadStatus == DownloadStatus.FAILED -> {
                    stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_DOWNLOAD_FAILED)
                    return
                }
            }
        }

        installManager.install(appId)
        while (true) {
            delay(200)
            val state = stateCenter.snapshot(appId)
            when (state.installStatus) {
                InstallStatus.INSTALLED -> {
                    stateCenter.updateUpgrade(appId, UpgradeStatus.NONE)
                    tracker.track("upgrade_success_$appId")
                    return
                }
                InstallStatus.FAILED -> {
                    stateCenter.updateUpgrade(appId, UpgradeStatus.FAILED, errorMessage = BusinessText.UPGRADE_INSTALL_FAILED)
                    return
                }
                else -> Unit
            }
        }
    }
}
