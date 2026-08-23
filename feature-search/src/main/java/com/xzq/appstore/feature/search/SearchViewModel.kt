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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** 页面数据加载与主动作执行使用的调度器，测试时可注入 TestDispatcher。 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel<SearchUiState>(SearchUiState()) {
    /** 搜索页状态订阅任务。 */
    private var observeJob: Job? = null

    /** 搜索页策略订阅任务。 */
    private var observePolicyJob: Job? = null

    /** 目录全量快照，用于生成搜索联想候选（避免每次输入都重新拉取目录）。 */
    private var catalogSnapshot: List<AppViewData> = emptyList()

    /** 搜索输入流：用 SharedFlow 而非 StateFlow，重复提交同一关键词也必须触发防抖重查，否则页面会卡在 Loading。 */
    private val keywordFlow = MutableSharedFlow<String>(extraBufferCapacity = KEYWORD_FLOW_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 搜索结果和详情共用的主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
        tracker = eventTracker,
        ioDispatcher = ioDispatcher,
    )

    /** 初始化搜索页数据并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { appManager.searchApps("") }
            }.onSuccess { catalogSnapshot = it }
            refresh(_uiState.value.keyword)
            observeStateChanges()
            observePolicyChanges()
            observeKeywordChanges()
        }
    }

    /** 根据关键字刷新搜索结果。
     * 关键字立即写入 UI 状态，重查询通过 [keywordFlow] 防抖合并，避免每个键击都全量重算。 */
    fun search(keyword: String) {
        _uiState.update { it.copy(keyword = keyword, screenState = SearchScreenState.Loading) }
        keywordFlow.tryEmit(keyword)
    }

    /** 监听防抖后的搜索输入并刷新结果。 */
    private fun observeKeywordChanges() {
        keywordFlow.debounce(SEARCH_DEBOUNCE_MS).onEach { keyword ->
            refresh(keyword)
        }.launchIn(viewModelScope)
    }

    /** 监听页面全局状态变化，并在变化时刷新当前关键字结果。
     * 进度事件高频触发，用周期采样限频，保证持续下载期间搜索结果仍会刷新。 */
    private fun observeStateChanges() {
        if (observeJob != null) {
            return
        }
        observeJob = stateCenter.observeAll().onEach {
            delay(STATE_REFRESH_SAMPLE_MS)
            refresh(_uiState.value.keyword)
        }.launchIn(viewModelScope)
    }

    /** 监听页面策略变化，并在变化时刷新当前关键字结果。 */
    private fun observePolicyChanges() {
        if (observePolicyJob != null) {
            return
        }
        observePolicyJob = policyCenter.observeSettings().debounce(POLICY_REFRESH_DEBOUNCE_MS).onEach { refresh(_uiState.value.keyword) }.launchIn(viewModelScope)
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
            withContext(ioDispatcher) {
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
        /** 搜索输入防抖窗口（毫秒）。 */
        const val SEARCH_DEBOUNCE_MS = 300L

        /** 高频状态变化刷新采样周期（毫秒）。 */
        const val STATE_REFRESH_SAMPLE_MS = 300L

        /** 低频策略变化刷新防抖窗口（毫秒）。 */
        const val POLICY_REFRESH_DEBOUNCE_MS = 300L

        /** 搜索联想候选最大条数。 */
        const val SUGGESTION_LIMIT = 6

        /** 搜索输入流缓冲容量：防抖消费很快，超出即丢最旧值，只保留最新输入语义。 */
        const val KEYWORD_FLOW_BUFFER = 8
    }
}
