package com.nio.appstore.feature.myapp

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MyAppViewModel(
    /** “我的应用”聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局状态变化。 */
    private val stateCenter: StateCenter,
) :
    BaseViewModel<MyAppUiState>(MyAppUiState()) {

    /** “我的应用”状态订阅任务。 */
    private var observeJob: Job? = null

    /** 初始化页面数据并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            refreshApps(showLoading = true)
            observeStateChanges()
        }
    }

    /** 监听页面全局状态变化，并在变化时刷新列表。 */
    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach {
                refreshApps()
            }
            .launchIn(viewModelScope)
    }

    /** 重新加载“我的应用”列表。 */
    private suspend fun refreshApps(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.value = _uiState.value.copy(screenState = MyAppScreenState.Loading)
        }
        runCatching {
            val apps = appManager.getMyApps()
            MyAppUiState(
                apps = apps,
                screenState = if (apps.isEmpty()) MyAppScreenState.Empty else MyAppScreenState.Content,
            )
        }.onSuccess { _uiState.value = it }
            .onFailure { throwable ->
                _uiState.value = MyAppUiState(
                    screenState = MyAppScreenState.Error(throwable.message.orEmpty()),
                )
            }
    }
}
