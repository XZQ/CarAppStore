package com.nio.appstore.feature.installcenter

import com.nio.appstore.data.model.InstallCenterControlsUiState
import com.nio.appstore.data.model.InstallSessionFilter
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats

data class InstallCenterUiState(
    val tasks: List<InstallTaskViewData> = emptyList(),
    val allTaskCount: Int = 0,
    val failedCount: Int = 0,
    val stats: TaskCenterStats = TaskCenterStats(),
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    val selectedSessionFilter: InstallSessionFilter = InstallSessionFilter.ALL,
    val batchRunnableCount: Int = 0,
    val clearFailedCount: Int = 0,
    val controlsUiState: InstallCenterControlsUiState = InstallCenterControlsUiState(),
)
