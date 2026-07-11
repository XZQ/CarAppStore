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
) : BaseViewModel<SearchUiState>(SearchUiState()) {
    /** 搜索页状态订阅任务。 */
    private var observeJob: Job? = null

    /** 搜索页策略订阅任务。 */
    private var observePolicyJob: Job? = null

    /** 目录全量快照，用于生成搜索联想候选（避免每次输入都重新拉取目录）。 */
    private var catalogSnapshot: List<AppViewData> = emptyList()

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
            runCatching {
                withContext(Dispatchers.IO) { appManager.searchApps("") }
            }.onSuccess { catalogSnapshot = it }
            refresh(_uiState.value.keyword)
            observeStateChanges()
            observePolicyChanges()
        }
    }

    /** 根据关键字刷新搜索结果。 */
    fun search(keyword: String) {
        _uiState.update { it.copy(keyword = keyword, screenState = SearchScreenState.Loading) }
        viewModelScope.launch { refresh(keyword) }
    }

    /** 监听页面全局状态变化，并在变化时刷新当前关键字结果。
     * 进度事件高频触发，用 debounce 合并，避免主线程反复全量重算。 */
    private fun observeStateChanges() {
        if (observeJob != null) {
            return
        }
        observeJob = stateCenter.observeAll().debounce(REFRESH_DEBOUNCE_MS).onEach { refresh(_uiState.value.keyword) }.launchIn(viewModelScope)
    }

    /** 监听页面策略变化，并在变化时刷新当前关键字结果。 */
    private fun observePolicyChanges() {
        if (observePolicyJob != null) {
            return
        }
        observePolicyJob = policyCenter.observeSettings().debounce(REFRESH_DEBOUNCE_MS).onEach { refresh(_uiState.value.keyword) }.launchIn(viewModelScope)
    }

    /** 处理搜索结果卡片主动作点击。 */
    fun onPrimaryClick(item: AppViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(appId = item.appId, action = item.primaryAction, packageName = item.packageName)
        }
    }

    /** 重新加载指定关键字的搜索结果与策略提示。
     * searchApps 涉及目录解析与本地存储读写，统一切到 IO 线程，避免阻塞主线程。 */
    private suspend fun refresh(keyword: String) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val apps = appManager.searchApps(keyword)
                apps to appManager.getPolicyPrompt()
            }
        }
        result.onSuccess { (apps, policyPrompt) ->
            val screen = when {
                keyword.isBlank() && apps.isEmpty() -> SearchScreenState.Idle
                apps.isEmpty() -> SearchScreenState.Empty
                else -> SearchScreenState.Content
            }
            // 联想候选：基于目录快照按关键词（名称/包名）前缀或包含匹配，限制条数。
            val suggestions = if (keyword.isBlank()) {
                emptyList()
            } else {
                catalogSnapshot.filter {
                    it.name.contains(keyword, ignoreCase = true) || it.packageName?.contains(keyword, ignoreCase = true) == true
                }.take(SUGGESTION_LIMIT)
            }
            _uiState.update {
                it.copy(apps = apps, suggestions = suggestions, policyPrompt = policyPrompt, screenState = screen)
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(apps = emptyList(), policyPrompt = "", screenState = SearchScreenState.Error(throwable.message.orEmpty()))
            }
        }
    }

    private companion object {
        /** 状态/策略变化刷新防抖窗口（毫秒）。 */
        const val REFRESH_DEBOUNCE_MS = 300L

        /** 搜索联想候选最大条数。 */
        const val SUGGESTION_LIMIT = 6
    }
}
