package com.nio.appstore.feature.upgrade

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.UpgradeCenterControlsUiState
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.domain.action.AppPrimaryActionExecutor
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class UpgradeViewModel(
    /** 升级中心聚合入口。 */
    private val appManager: AppManager,
    /** 用于监听全局升级状态变化。 */
    private val stateCenter: StateCenter,
    /** 升级业务入口。 */
    private val upgradeManager: UpgradeManager,
) : BaseViewModel<UpgradeUiState>(UpgradeUiState()) {

    /** 升级中心状态订阅任务。 */
    private var observeJob: Job? = null
    /** 当前选中的任务筛选条件。 */
    private var selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL

    /** 升级中心单项任务主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        upgradeManager = upgradeManager,
    )

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
            primaryActionExecutor.execute(
                appId = item.appId,
                action = item.primaryAction,
                packageName = item.packageName,
            )
            refresh()
        }
    }

    /** 重试失败升级任务。 */
    fun onRetryFailed() {
        viewModelScope.launch {
            val failed = appManager.getUpgradeTasks().filter {
                it.overallStatus == com.nio.appstore.data.model.TaskOverallStatus.FAILED || it.primaryAction == PrimaryAction.UPGRADE
            }
            failed.forEach { task ->
                primaryActionExecutor.execute(task.appId, task.primaryAction, task.packageName)
            }
            refresh()
        }
    }


    /** 批量启动当前筛选范围内所有可执行升级任务。 */
    fun onStartAllRunnable() {
        viewModelScope.launch {
            val runnable = appManager.getUpgradeTasks().filter {
                (it.primaryAction == PrimaryAction.UPGRADE) && selectedFilter.matches(it.overallStatus)
            }
            runnable.forEach { task -> primaryActionExecutor.execute(task.appId, task.primaryAction, task.packageName) }
            refresh()
        }
    }

    /** 切换升级中心筛选条件。 */
    fun onCycleFilter() {
        selectedFilter = selectedFilter.next()
        viewModelScope.launch { refresh() }
    }

    /** 监听页面全局状态变化。 */
    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /** 重新计算升级中心页面状态。 */
    private suspend fun refresh(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.value = _uiState.value.copy(screenState = UpgradeScreenState.Loading)
        }
        runCatching {
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
                controlsUiState = UpgradeCenterControlsUiState(
                    runnableCount = runnableCount,
                    failedCount = failedCount,
                ),
                screenState = if (visible.isEmpty()) {
                    UpgradeScreenState.Empty
                } else {
                    UpgradeScreenState.Content
                },
            )
        }.onSuccess { _uiState.value = it }
            .onFailure { throwable ->
                _uiState.value = UpgradeUiState(
                    selectedFilter = selectedFilter,
                    screenState = UpgradeScreenState.Error(throwable.message.orEmpty()),
                )
            }
    }
}
