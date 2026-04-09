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
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
    private val upgradeManager: UpgradeManager,
) : BaseViewModel<UpgradeUiState>(UpgradeUiState()) {

    private var observeJob: Job? = null
    private var selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL

    fun load() {
        viewModelScope.launch {
            refresh()
            observeStateChanges()
        }
    }

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


    fun onStartAllRunnable() {
        viewModelScope.launch {
            val runnable = appManager.getUpgradeTasks().filter {
                (it.primaryAction == PrimaryAction.UPGRADE) && selectedFilter.matches(it.overallStatus)
            }
            runnable.forEach { task -> upgradeManager.startUpgrade(task.appId) }
            refresh()
        }
    }

    fun onCycleFilter() {
        selectedFilter = selectedFilter.next()
        viewModelScope.launch { refresh() }
    }

    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

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
