package com.nio.appstore.feature.downloadmanager

import com.nio.appstore.data.model.DownloadCenterPreferencesUiState
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats

/**
 * DownloadManagerUiState 描述下载中心页面的完整界面状态。
 */
data class DownloadManagerUiState(
    /** 当前筛选条件下展示的下载任务列表。 */
    val tasks: List<DownloadTaskViewData> = emptyList(),
    /** 同页展示的待安装任务列表。 */
    val installTasks: List<InstallTaskViewData> = emptyList(),
    /** 筛选前的任务总数。 */
    val allTaskCount: Int = 0,
    /** 当前选中的任务筛选条件。 */
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    /** 下载偏好扩展区的界面状态。 */
    val preferencesUiState: DownloadCenterPreferencesUiState = DownloadCenterPreferencesUiState(),
    /** 筛选前的失败任务总数。 */
    val failedCount: Int = 0,
    /** 下载任务的聚合统计数据。 */
    val downloadStats: TaskCenterStats = TaskCenterStats(),
    /** 安装任务的聚合统计数据。 */
    val installStats: TaskCenterStats = TaskCenterStats(),
    /** 当前筛选范围内可直接安装的下载任务数。 */
    val readyInstallCount: Int = 0,
    /** 当前筛选范围内实际展示的任务总数。 */
    val visibleTaskCount: Int = 0,
    /** 下载中心页面头部展示用的合并统计数据。 */
    val combinedStats: TaskCenterStats = TaskCenterStats(),
    /** 当前显式页面状态机。 */
    val screenState: DownloadManagerScreenState = DownloadManagerScreenState.Loading,
)

/**
 * DownloadManagerScreenState 描述下载中心当前页面状态。
 */
sealed interface DownloadManagerScreenState {
    /** 页面正在同步下载中心数据。 */
    data object Loading : DownloadManagerScreenState

    /** 页面已有可展示内容。 */
    data object Content : DownloadManagerScreenState

    /** 页面暂无可展示内容。 */
    data object Empty : DownloadManagerScreenState

    /**
     * 页面加载失败。
     *
     * @property message 当前需要展示给用户的失败原因。
     */
    data class Error(
        /** 当前需要展示给用户的失败原因。 */
        val message: String,
    ) : DownloadManagerScreenState
}
