package com.xzq.appstore.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.upgrade.UpgradeManager

class SearchViewModelFactory(
    /** 提供搜索页聚合数据。 */
    private val appManager: AppManager,
    /** 提供搜索结果实时状态。 */
    private val stateCenter: StateCenter,
    /** 提供搜索结果卡片主动作的下载入口。 */
    private val downloadManager: DownloadManager,
    /** 提供搜索结果卡片主动作的安装入口。 */
    private val installManager: InstallManager,
    /** 提供搜索结果卡片主动作的升级入口。 */
    private val upgradeManager: UpgradeManager,
    /** 提供搜索页策略观察能力。 */
    private val policyCenter: PolicyCenter,
    /** 提供本地事件打点能力。 */
    private val eventTracker: EventTracker,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建搜索页 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(
            appManager = appManager,
            stateCenter = stateCenter,
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
            policyCenter = policyCenter,
            eventTracker = eventTracker,
        ) as T
    }
}
