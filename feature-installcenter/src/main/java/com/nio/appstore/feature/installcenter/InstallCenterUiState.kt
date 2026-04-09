package com.nio.appstore.feature.installcenter

import com.nio.appstore.data.model.InstallCenterControlsUiState
import com.nio.appstore.data.model.InstallSessionFilter
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats

data class InstallCenterUiState(
    /** 当前筛选条件下展示的安装任务列表。 */
    val tasks: List<InstallTaskViewData> = emptyList(),
    /** 筛选前的安装任务总数。 */
    val allTaskCount: Int = 0,
    /** 筛选前的失败任务总数。 */
    val failedCount: Int = 0,
    /** 聚合后的任务统计数据。 */
    val stats: TaskCenterStats = TaskCenterStats(),
    /** 当前选中的任务筛选条件。 */
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    /** 当前选中的会话筛选条件。 */
    val selectedSessionFilter: InstallSessionFilter = InstallSessionFilter.ALL,
    /** 当前筛选范围内可直接执行的任务数。 */
    val batchRunnableCount: Int = 0,
    /** 当前可清理的失败任务数。 */
    val clearFailedCount: Int = 0,
    /** 安装中心扩展控制区的界面状态。 */
    val controlsUiState: InstallCenterControlsUiState = InstallCenterControlsUiState(),
)
