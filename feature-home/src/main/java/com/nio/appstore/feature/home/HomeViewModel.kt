package com.nio.appstore.feature.home

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    /** 首页应用聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局状态变化并刷新首页。 */
    private val stateCenter: StateCenter,
) :
    BaseViewModel<HomeUiState>(HomeUiState()) {

    /** 首页状态订阅任务，避免重复注册观察。 */
    private var observeJob: Job? = null

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

    /** 重新加载首页应用列表和策略提示。 */
    private suspend fun refreshApps() {
        _uiState.value = HomeUiState(
            loading = false,
            apps = appManager.getHomeApps(),
            policyPrompt = appManager.getPolicyPrompt(),
        )
    }
}
