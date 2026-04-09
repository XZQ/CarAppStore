package com.nio.appstore.feature.downloadmanager

import com.nio.appstore.data.model.DownloadCenterPreferencesUiState
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats

data class DownloadManagerUiState(
    val tasks: List<DownloadTaskViewData> = emptyList(),
    val installTasks: List<InstallTaskViewData> = emptyList(),
    val allTaskCount: Int = 0,
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    val preferencesUiState: DownloadCenterPreferencesUiState = DownloadCenterPreferencesUiState(),
    val failedCount: Int = 0,
    val downloadStats: TaskCenterStats = TaskCenterStats(),
    val installStats: TaskCenterStats = TaskCenterStats(),
)
