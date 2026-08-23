package com.xzq.appstore.feature.home

import androidx.lifecycle.viewModelScope
import com.xzq.appstore.common.base.BaseViewModel
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.domain.action.AppPrimaryActionExecutor
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val upgradeManager: UpgradeManager,
    private val policyCenter: PolicyCenter,
    private val eventTracker: EventTracker = EventTracker(),
    /** 首页数据加载使用的调度器，测试时可注入 TestDispatcher 让异步任务确定性执行。 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel<HomeUiState>(HomeUiState()) {
    /** 首页状态订阅任务。 */
    private var observeJob: Job? = null

    /** 首页策略订阅任务。 */
    private var observePolicyJob: Job? = null

    /** 首页卡片和详情页共用的主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
        tracker = eventTracker,
        ioDispatcher = ioDispatcher,
    )

    /** 初始化首页数据，并监听任务状态和策略变化。 */
    fun load() {
        viewModelScope.launch {
            refresh(showLoading = true)
            observeStateChanges()
            observePolicyChanges()
        }
    }

    /** 执行首页卡片主动作。 */
    fun onPrimaryClick(item: AppViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(appId = item.appId, action = item.primaryAction, packageName = item.packageName)
        }
    }

    /** 监听页面全局状态变化，并刷新推荐列表。
     * 进度事件高频触发，用 debounce 合并，避免主线程反复全量重算。 */
    private fun observeStateChanges() {
        if (observeJob != null) {
            return
        }
        observeJob = stateCenter.observeAll().debounce(REFRESH_DEBOUNCE_MS).onEach { refresh() }.launchIn(viewModelScope)
    }

    /** 监听页面策略变化，并刷新策略提示。 */
    private fun observePolicyChanges() {
        if (observePolicyJob != null) {
            return
        }
        observePolicyJob = policyCenter.observeSettings().debounce(REFRESH_DEBOUNCE_MS).onEach { refresh() }.launchIn(viewModelScope)
    }

    /** 重新拉取首页推荐应用与策略提示。
     * 目录聚合涉及本地存储读写与系统包查询，统一切到 IO 线程，避免阻塞主线程。 */
    private suspend fun refresh(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.update { it.copy(loading = true, screenState = HomeScreenState.Loading) }
        }
        runCatching {
            withContext(ioDispatcher) {
                val apps = appManager.getHomeApps()
                val recentApps = appManager.getRecentlyUsedApps()
                HomeUiState(
                    loading = false,
                    apps = apps,
                    recentApps = recentApps,
                    policyPrompt = appManager.getPolicyPrompt(),
                    screenState = if (apps.isEmpty()) HomeScreenState.Empty else HomeScreenState.Content,
                )
            }
        }.onSuccess { _uiState.value = it }.onFailure { throwable ->
            _uiState.value = HomeUiState(loading = false, policyPrompt = "", screenState = HomeScreenState.Error(throwable.message.orEmpty()))
        }
    }

    private companion object {
        /** 状态/策略变化刷新防抖窗口（毫秒）。 */
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}
