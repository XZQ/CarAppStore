package com.xzq.appstore.feature.search

import androidx.lifecycle.viewModelScope
import com.xzq.appstore.common.base.BaseViewModel
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.data.model.CatalogQuery
import com.xzq.appstore.common.navigation.CatalogSection
import com.xzq.appstore.domain.action.AppPrimaryActionExecutor
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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

    private var refreshJob: Job? = null
    private var refreshPending = false
    private var requestGeneration = 0L
    private var loaded = false
    private var section = CatalogSection.Software

    /** 搜索结果和详情共用的主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        stateCenter = stateCenter,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
        tracker = eventTracker,
        ioDispatcher = ioDispatcher,
    )

    /** 初始化搜索页数据并开始监听状态变化。 */
    fun load(page: CatalogPage = CatalogPage.Software) {
        if (loaded) return
        loaded = true
        section = CatalogSection.valueOf(page.name)
        observeStateChanges()
        observePolicyChanges()
        requestRefresh()
    }

    /** 根据关键字刷新搜索结果。
     * 关键字立即写入 UI 状态，延迟重查询并取消旧请求。 */
    fun search(keyword: String) {
        _uiState.update { it.copy(keyword = keyword, screenState = SearchScreenState.Loading) }
        requestRefresh(SEARCH_DEBOUNCE_MS)
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        requestRefresh()
    }

    fun retry() = requestRefresh()

    /** 监听页面全局状态变化，并在变化时刷新当前关键字结果。
     * 进度事件高频触发，用周期采样限频，保证持续下载期间搜索结果仍会刷新。 */
    private fun observeStateChanges() {
        if (observeJob != null) {
            return
        }
        observeJob = stateCenter.observeAll().onEach {
            delay(STATE_REFRESH_SAMPLE_MS)
            requestStateRefresh()
        }.launchIn(viewModelScope)
    }

    /** 监听页面策略变化，并在变化时刷新当前关键字结果。 */
    private fun observePolicyChanges() {
        if (observePolicyJob != null) {
            return
        }
        observePolicyJob = policyCenter.observeSettings().debounce(POLICY_REFRESH_DEBOUNCE_MS).onEach {
            requestStateRefresh()
        }.launchIn(viewModelScope)
    }

    /** 处理搜索结果卡片主动作点击。 */
    fun onPrimaryClick(item: AppViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(appId = item.appId, action = item.primaryAction, packageName = item.packageName)
        }
    }

    private fun requestStateRefresh() {
        if (refreshJob?.isActive == true) {
            refreshPending = true
        } else {
            requestRefresh(showLoading = false)
        }
    }

    /** 重新加载指定关键字的搜索结果与策略提示。
     * searchApps 涉及目录解析与本地存储读写，统一切到 IO 线程，避免阻塞主线程。 */
    private fun requestRefresh(debounceMs: Long = 0L, showLoading: Boolean = true) {
        val generation = ++requestGeneration
        refreshJob?.cancel()
        refreshPending = false
        val query = CatalogQuery(_uiState.value.keyword.trim(), section, _uiState.value.selectedCategory)
        if (showLoading) _uiState.update { it.copy(screenState = SearchScreenState.Loading) }
        refreshJob = viewModelScope.launch {
            delay(debounceMs)
            try {
                val (catalog, apps, prompt) = withContext(ioDispatcher) {
                    val catalog = if (catalogSnapshot.isEmpty()) appManager.getCatalogApps(CatalogQuery(section = section)) else catalogSnapshot
                    val apps = appManager.getCatalogApps(query)
                    Triple(catalog, apps.distinctBy { it.appId }, appManager.getPolicyPrompt())
                }
                if (generation != requestGeneration) return@launch
                catalogSnapshot = catalog
                val suggestions = if (query.keyword.isBlank()) emptyList() else catalog.filter {
                    it.name.contains(query.keyword, true) || it.packageName?.contains(query.keyword, true) == true
                }.take(SUGGESTION_LIMIT)
                _uiState.update { it.copy(apps = apps, suggestions = suggestions, policyPrompt = prompt,
                    categories = catalog.map { app -> app.category }.filter { category -> category.isNotBlank() }.distinct().sorted(),
                    screenState = if (apps.isEmpty()) SearchScreenState.Empty else SearchScreenState.Content) }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (failure: Exception) {
                if (generation == requestGeneration) _uiState.update {
                    it.copy(apps = emptyList(), suggestions = emptyList(), policyPrompt = "", screenState = SearchScreenState.Error(failure.message.orEmpty()))
                }
            } finally {
                // 查询期间最后一个任务/策略事件也必须被消费，避免停留在过期的按钮状态。
                if (generation == requestGeneration && refreshPending && currentCoroutineContext().isActive) {
                    requestRefresh(showLoading = false)
                }
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

    }
}
