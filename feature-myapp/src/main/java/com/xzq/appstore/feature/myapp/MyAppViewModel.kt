package com.xzq.appstore.feature.myapp

import androidx.lifecycle.viewModelScope
import com.xzq.appstore.common.base.BaseViewModel
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.state.StateCenter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyAppViewModel(
    /** “我的应用”聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局状态变化。 */
    private val stateCenter: StateCenter,
    /** 页面数据加载使用的调度器，测试时可注入 TestDispatcher。 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel<MyAppUiState>(MyAppUiState()) {
    /** “我的应用”状态订阅任务。 */
    private var observeJob: Job? = null

    /** 初始化页面数据并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            refreshApps(showLoading = true)
            observeStateChanges()
        }
    }

    /** 监听页面全局状态变化，并在变化时刷新列表。
     * 进度事件高频触发，用周期采样限频，保证持续下载期间应用状态仍会刷新。 */
    private fun observeStateChanges() {
        if (observeJob != null) {
            return
        }
        observeJob = stateCenter.observeAll().onEach {
            delay(STATE_REFRESH_SAMPLE_MS)
            refreshApps()
        }.launchIn(viewModelScope)
    }

    /** 重新加载“我的应用”列表。
     * 列表聚合涉及本地存储读写与系统包查询，统一切到 IO 线程，避免阻塞主线程。 */
    private suspend fun refreshApps(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.update { it.copy(screenState = MyAppScreenState.Loading) }
        }
        runCatching {
            withContext(ioDispatcher) {
                val apps = appManager.getMyApps()
                MyAppUiState(apps = apps, screenState = if (apps.isEmpty()) MyAppScreenState.Empty else MyAppScreenState.Content)
            }
        }.onSuccess { _uiState.value = it }.onFailure { throwable ->
            _uiState.value = MyAppUiState(screenState = MyAppScreenState.Error(throwable.message.orEmpty()))
        }
    }

    private companion object {
        /** 高频状态变化刷新采样周期（毫秒）。 */
        const val STATE_REFRESH_SAMPLE_MS = 300L
    }
}
