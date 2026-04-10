package com.nio.appstore.feature.upgrade

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.UpgradeCenterControlsUiState
import com.nio.appstore.data.model.UpgradeTaskViewData
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

    /** 初始化升级中心并开始监听状态变化。 */
    fun load() {
        viewModelScope.launch {
            refresh()
            observeStateChanges()
        }
    }

    /** 处理升级任务主按钮点击。 */
    fun onPrimaryClick(item: UpgradeTaskViewData) {
        viewModelScope.launch {
            when (item.primaryAction) {
                PrimaryAction.UPGRADE -> upgradeManager.startUpgrade(item.appId)
                PrimaryAction.OPEN -> appManager.openApp(item.packageName)
                else -> Unit
            }
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
                if (task.primaryAction == PrimaryAction.UPGRADE) {
                    upgradeManager.startUpgrade(task.appId)
                }
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
            runnable.forEach { task -> upgradeManager.startUpgrade(task.appId) }
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
    private suspend fun refresh() {
        val allTasks = appManager.getUpgradeTasks()
        val visible = allTasks.filter { selectedFilter.matches(it.overallStatus) }
        val failedCount = allTasks.count { !it.reasonText.isNullOrBlank() }
        val runnableCount = visible.count { it.primaryAction == PrimaryAction.UPGRADE }
        _uiState.value = UpgradeUiState(
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
        )
    }
}
