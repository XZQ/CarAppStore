package com.nio.appstore.feature.home

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.domain.action.AppPrimaryActionExecutor
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    /** 首页应用聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局状态变化并刷新首页。 */
    private val stateCenter: StateCenter,
    /** 首页卡片发起下载时复用的下载入口。 */
    private val downloadManager: DownloadManager,
    /** 首页卡片发起安装时复用的安装入口。 */
    private val installManager: InstallManager,
    /** 首页卡片发起升级时复用的升级入口。 */
    private val upgradeManager: UpgradeManager,
) :
    BaseViewModel<HomeUiState>(HomeUiState()) {

    /** 首页状态订阅任务，避免重复注册观察。 */
    private var observeJob: Job? = null

    /** 首页卡片和详情共用的主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
    )

    /** 初始化首页数据并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            refreshApps()
            observeStateChanges()
        }
    }

    /** 监听页面全局状态变化，并在变化时刷新首页卡片。 */
    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach {
                refreshApps()
            }
            .launchIn(viewModelScope)
    }

    /** 处理首页卡片主动作点击。 */
    fun onPrimaryClick(item: AppViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(
                appId = item.appId,
                action = item.primaryAction,
                packageName = item.packageName,
            )
        }
    }

    /** 重新加载首页应用列表和策略提示。 */
    private suspend fun refreshApps() {
        _uiState.value = HomeUiState(
            loading = false,
            apps = appManager.getHomeApps(),
            policyPrompt = appManager.getPolicyPrompt(),
        )
    }
}
