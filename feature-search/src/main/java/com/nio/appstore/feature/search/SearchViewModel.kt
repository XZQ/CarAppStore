package com.nio.appstore.feature.search

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SearchViewModel(
    /** 搜索结果聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局状态变化。 */
    private val stateCenter: StateCenter,
) :
    BaseViewModel<SearchUiState>(SearchUiState()) {

    /** 搜索页状态订阅任务。 */
    private var observeJob: Job? = null

    /** 初始化搜索页数据并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            refresh(_uiState.value.keyword)
            observeStateChanges()
        }
    }

    /** 根据关键字刷新搜索结果。 */
    fun search(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
        viewModelScope.launch { refresh(keyword) }
    }

    /** 监听页面全局状态变化，并在变化时刷新当前关键字结果。 */
    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh(_uiState.value.keyword) }
            .launchIn(viewModelScope)
    }

    /** 重新加载指定关键字的搜索结果与策略提示。 */
    private suspend fun refresh(keyword: String) {
        _uiState.value = _uiState.value.copy(
            apps = appManager.searchApps(keyword),
            policyPrompt = appManager.getPolicyPrompt(),
        )
    }
}
