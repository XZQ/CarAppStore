package com.xzq.appstore.feature.upgrade

import androidx.lifecycle.viewModelScope
import com.xzq.appstore.common.base.BaseViewModel
import com.xzq.appstore.data.model.TaskCenterFilter
import com.xzq.appstore.data.model.UpgradeCenterControlsUiState
import com.xzq.appstore.data.model.UpgradeTaskViewData
import com.xzq.appstore.domain.action.AppPrimaryActionExecutor
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.state.PrimaryAction
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xzq.appstore.data.model.TaskOverallStatus

class UpgradeViewModel(
    /** 升级中心聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局升级状态变化。 */
    private val stateCenter: StateCenter,
    /** 升级业务入口。 */
    private val upgradeManager: UpgradeManager,
    /** 页面数据加载使用的调度器，测试时可注入 TestDispatcher。 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel<UpgradeUiState>(UpgradeUiState()) {
    /** 升级中心状态订阅任务。 */
    private var observeJob: Job? = null

    /** 当前选中的任务筛选条件。 */
    private var selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL

    /** 升级中心单项任务主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(appManager = appManager, upgradeManager = upgradeManager, ioDispatcher = ioDispatcher)

    /** 初始化升级中心并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            refresh(showLoading = true)
            observeStateChanges()
        }
    }

    /** 处理升级任务主按钮点击。 */
    fun onPrimaryClick(item: UpgradeTaskViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(appId = item.appId, action = item.primaryAction, packageName = item.packageName)
            refresh()
        }
    }

    /** 重试失败升级任务。 */
    fun onRetryFailed() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val failed = appManager.getUpgradeTasks().filter {
                    it.overallStatus == TaskOverallStatus.FAILED || it.primaryAction == PrimaryAction.UPGRADE
                }
                failed.forEach { task ->
                    primaryActionExecutor.execute(task.appId, task.primaryAction, task.packageName)
                }
            }
            refresh()
        }
    }

    /** 批量启动当前筛选范围内所有可执行升级任务。 */
    fun onStartAllRunnable() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val runnable = appManager.getUpgradeTasks().filter {
                    (it.primaryAction == PrimaryAction.UPGRADE) && selectedFilter.matches(it.overallStatus)
                }
                runnable.forEach { task -> primaryActionExecutor.execute(task.appId, task.primaryAction, task.packageName) }
            }
            refresh()
        }
    }

    /** 切换升级中心筛选条件。 */
    fun onCycleFilter() {
        selectedFilter = selectedFilter.next()
        viewModelScope.launch { refresh() }
    }

    /** 监听页面全局状态变化。
     * 进度事件高频触发，用周期采样限频，保证持续下载期间升级任务仍会刷新。 */
    private fun observeStateChanges() {
        if (observeJob != null) {
            return
        }
        observeJob = stateCenter.observeAll().onEach {
            delay(STATE_REFRESH_SAMPLE_MS)
            refresh()
        }.launchIn(viewModelScope)
    }

    /** 重新计算升级中心页面状态。
     * 任务聚合涉及目录过滤、升级信息查询和系统包查询，统一切到 IO 线程，避免阻塞主线程。 */
    private suspend fun refresh(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.update { it.copy(screenState = UpgradeScreenState.Loading) }
        }
        runCatching {
            withContext(ioDispatcher) {
                val allTasks = appManager.getUpgradeTasks()
                val visible = allTasks.filter { selectedFilter.matches(it.overallStatus) }
                val failedCount = allTasks.count { !it.reasonText.isNullOrBlank() }
                val runnableCount = visible.count { it.primaryAction == PrimaryAction.UPGRADE }
                UpgradeUiState(
                    tasks = visible,
                    availableCount = allTasks.size,
                    failedCount = failedCount,
                    stats = appManager.getUpgradeTaskStats(),
                    selectedFilter = selectedFilter,
                    batchRunnableCount = runnableCount,
                    showFailurePanel = failedCount > 0,
                    controlsUiState = UpgradeCenterControlsUiState(runnableCount = runnableCount, failedCount = failedCount),
                    screenState = if (visible.isEmpty()) {
                        UpgradeScreenState.Empty
                    } else {
                        UpgradeScreenState.Content
                    },
                )
            }
        }.onSuccess { _uiState.value = it }.onFailure { throwable ->
            _uiState.value = UpgradeUiState(selectedFilter = selectedFilter, screenState = UpgradeScreenState.Error(throwable.message.orEmpty()))
        }
    }

    private companion object {
        /** 高频状态变化刷新采样周期（毫秒）。 */
        const val STATE_REFRESH_SAMPLE_MS = 300L
    }
}
