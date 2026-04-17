package com.nio.appstore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager

class HomeViewModelFactory(
    /** 提供首页应用聚合数据。 */
    private val appManager: AppManager,
    /** 提供首页应用实时状态。 */
    private val stateCenter: StateCenter,
    /** 提供首页卡片主动作的下载入口。 */
    private val downloadManager: DownloadManager,
    /** 提供首页卡片主动作的安装入口。 */
    private val installManager: InstallManager,
    /** 提供首页卡片主动作的升级入口。 */
    private val upgradeManager: UpgradeManager,
    /** 提供首页策略观察能力。 */
    private val policyCenter: PolicyCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建首页 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            appManager = appManager,
            stateCenter = stateCenter,
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
            policyCenter = policyCenter,
        ) as T
    }
}
