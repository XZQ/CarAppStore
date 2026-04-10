package com.nio.appstore.domain.action

import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.upgrade.UpgradeManager

/**
 * AppPrimaryActionExecutor 统一分发应用卡片和详情页上的主动作。
 */
class AppPrimaryActionExecutor(
    /** 提供打开应用等应用级动作能力。 */
    private val appManager: AppManager,
    /** 提供下载动作能力。 */
    private val downloadManager: DownloadManager? = null,
    /** 提供安装动作能力。 */
    private val installManager: InstallManager? = null,
    /** 提供升级动作能力。 */
    private val upgradeManager: UpgradeManager? = null,
) {

    /**
     * 执行指定应用当前对应的主动作。
     *
     * @param appId 当前动作对应的应用标识
     * @param action 当前要执行的主动作
     * @param packageName 打开应用时使用的包名，可为空
     */
    suspend fun execute(
        appId: String,
        action: PrimaryAction,
        packageName: String? = null,
    ) {
        when (action) {
            PrimaryAction.DOWNLOAD, PrimaryAction.RETRY_DOWNLOAD -> downloadManager?.startDownload(appId)
            PrimaryAction.PAUSE -> downloadManager?.pauseDownload(appId)
            PrimaryAction.RESUME -> downloadManager?.resumeDownload(appId)
            PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> {
                installManager?.install(appId)
                upgradeManager?.checkUpgrade(appId)
            }
            PrimaryAction.OPEN -> packageName?.let { appManager.openApp(it) }
            PrimaryAction.UPGRADE -> upgradeManager?.startUpgrade(appId)
            PrimaryAction.DISABLED -> Unit
        }
    }
}
