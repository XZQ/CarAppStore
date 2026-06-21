package com.xzq.appstore.feature.search

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SearchViewModel(
    /** 搜索结果聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局状态变化。 */
    private val stateCenter: StateCenter,
    /** 搜索结果卡片发起下载时复用的下载入口。 */
    private val downloadManager: DownloadManager,
    /** 搜索结果卡片发起安装时复用的安装入口。 */
    private val installManager: InstallManager,
    /** 搜索结果卡片发起升级时复用的升级入口。 */
    private val upgradeManager: UpgradeManager,
    /** 用于监听页面策略变化。 */
    private val policyCenter: PolicyCenter,
    private val eventTracker: EventTracker = EventTracker(),
) :
    BaseViewModel<SearchUiState>(SearchUiState()) {

    /** 搜索页状态订阅任务。 */
    private var observeJob: Job? = null
    /** 搜索页策略订阅任务。 */
    private var observePolicyJob: Job? = null

    /** 搜索结果和详情共用的主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
        tracker = eventTracker,
    )

    /** 初始化搜索页数据并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            refresh(_uiState.value.keyword)
            observeStateChanges()
            observePolicyChanges()
        }
    }

    /** 根据关键字刷新搜索结果。 */
    fun search(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword, screenState = SearchScreenState.Loading)
        viewModelScope.launch { refresh(keyword) }
    }

    /** 监听页面全局状态变化，并在变化时刷新当前关键字结果。 */
    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh(_uiState.value.keyword) }
            .launchIn(viewModelScope)
    }

    /** 监听页面策略变化，并在变化时刷新当前关键字结果。 */
    private fun observePolicyChanges() {
        if (observePolicyJob != null) return
        observePolicyJob = policyCenter.observeSettings()
            .onEach { refresh(_uiState.value.keyword) }
            .launchIn(viewModelScope)
    }

    /** 处理搜索结果卡片主动作点击。 */
    fun onPrimaryClick(item: AppViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(
                appId = item.appId,
                action = item.primaryAction,
                packageName = item.packageName,
            )
        }
    }

    /** 重新加载指定关键字的搜索结果与策略提示。 */
    private suspend fun refresh(keyword: String) {
        runCatching {
            val apps = appManager.searchApps(keyword)
            _uiState.value.copy(
                apps = apps,
                policyPrompt = appManager.getPolicyPrompt(),
                screenState = when {
                    keyword.isBlank() && apps.isEmpty() -> SearchScreenState.Idle
                    apps.isEmpty() -> SearchScreenState.Empty
                    else -> SearchScreenState.Content
                },
            )
        }.onSuccess { _uiState.value = it }
            .onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    apps = emptyList(),
                    policyPrompt = "",
                    screenState = SearchScreenState.Error(throwable.message.orEmpty()),
                )
            }
    }
}
