package com.nio.appstore.domain.state

import com.nio.appstore.domain.text.BusinessText

object StateReducer {
    /** 将底层原始状态归约为界面可直接消费的文案和主动作。 */
    fun reduce(base: AppState): AppState {
        // 状态文案优先级按“错误 > 升级 > 安装 > 下载 > 默认”顺序归约。
        val statusText = when {
            base.errorMessage != null -> base.errorMessage
            base.upgradeStatus == UpgradeStatus.UPGRADING -> BusinessText.STATUS_UPGRADING
            base.upgradeStatus == UpgradeStatus.FAILED -> BusinessText.STATUS_UPGRADE_FAILED
            base.installStatus == InstallStatus.INSTALLING -> BusinessText.STATUS_INSTALLING
            base.installStatus == InstallStatus.PENDING_USER_ACTION -> BusinessText.STATUS_WAITING_SYSTEM_CONFIRM
            base.installStatus == InstallStatus.WAITING -> BusinessText.STATUS_WAITING_INSTALL
            base.installStatus == InstallStatus.FAILED -> BusinessText.STATUS_INSTALL_FAILED
            base.installStatus == InstallStatus.INSTALLED && base.upgradeStatus == UpgradeStatus.AVAILABLE -> BusinessText.STATUS_UPGRADE_AVAILABLE
            base.installStatus == InstallStatus.INSTALLED -> BusinessText.STATUS_INSTALLED
            base.downloadStatus == DownloadStatus.RUNNING -> BusinessText.downloading(base.progress)
            base.downloadStatus == DownloadStatus.WAITING -> BusinessText.STATUS_WAITING_DOWNLOAD
            base.downloadStatus == DownloadStatus.PAUSED -> BusinessText.paused(base.progress)
            base.downloadStatus == DownloadStatus.COMPLETED -> BusinessText.STATUS_DOWNLOAD_COMPLETED
            base.downloadStatus == DownloadStatus.FAILED -> BusinessText.STATUS_DOWNLOAD_FAILED
            base.downloadStatus == DownloadStatus.CANCELED -> BusinessText.STATUS_CANCELED
            else -> BusinessText.STATUS_NOT_INSTALLED
        }

        // 主动作优先反映当前最重要、且用户可以执行的下一步动作。
        val primaryAction = when {
            base.upgradeStatus == UpgradeStatus.UPGRADING -> PrimaryAction.DISABLED
            base.installStatus == InstallStatus.PENDING_USER_ACTION -> PrimaryAction.DISABLED
            base.installStatus == InstallStatus.INSTALLING -> PrimaryAction.DISABLED
            base.installStatus == InstallStatus.FAILED -> PrimaryAction.RETRY_INSTALL
            base.installStatus == InstallStatus.INSTALLED && base.upgradeStatus == UpgradeStatus.AVAILABLE -> PrimaryAction.UPGRADE
            base.installStatus == InstallStatus.INSTALLED -> PrimaryAction.OPEN
            base.downloadStatus == DownloadStatus.COMPLETED -> PrimaryAction.INSTALL
            base.downloadStatus == DownloadStatus.RUNNING -> PrimaryAction.PAUSE
            base.downloadStatus == DownloadStatus.PAUSED -> PrimaryAction.RESUME
            base.downloadStatus == DownloadStatus.FAILED || base.downloadStatus == DownloadStatus.CANCELED -> PrimaryAction.RETRY_DOWNLOAD
            else -> PrimaryAction.DOWNLOAD
        }

        return base.copy(statusText = statusText, primaryAction = primaryAction)
    }
}
