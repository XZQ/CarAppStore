package com.nio.appstore.feature.upgrade

import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.UpgradeCenterControlsUiState
import com.nio.appstore.data.model.UpgradeTaskViewData

data class UpgradeUiState(
    val tasks: List<UpgradeTaskViewData> = emptyList(),
    val availableCount: Int = 0,
    val failedCount: Int = 0,
    val stats: TaskCenterStats = TaskCenterStats(),
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    val batchRunnableCount: Int = 0,
    val controlsUiState: UpgradeCenterControlsUiState = UpgradeCenterControlsUiState(),
)
