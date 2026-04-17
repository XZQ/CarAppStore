package com.nio.appstore.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager

class DetailViewModelFactory(
    /** 提供详情页应用聚合数据。 */
    private val appManager: AppManager,
    /** 负责详情页的下载动作。 */
    private val downloadManager: DownloadManager,
    /** 负责详情页的安装动作。 */
    private val installManager: InstallManager,
    /** 负责详情页的升级动作。 */
    private val upgradeManager: UpgradeManager,
    /** 负责监听详情页应用运行态。 */
    private val stateCenter: StateCenter,
    /** 负责监听详情页策略变化。 */
    private val policyCenter: PolicyCenter,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    /** 创建详情页 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetailViewModel(appManager, downloadManager, installManager, upgradeManager, stateCenter, policyCenter) as T
    }
}
