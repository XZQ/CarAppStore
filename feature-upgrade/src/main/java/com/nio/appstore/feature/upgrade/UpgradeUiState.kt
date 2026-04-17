package com.nio.appstore.feature.upgrade

import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.UpgradeCenterControlsUiState
import com.nio.appstore.data.model.UpgradeTaskViewData

/**
 * UpgradeUiState 描述升级中心页面的完整界面状态。
 */
data class UpgradeUiState(
    /** 当前筛选条件下展示的升级任务列表。 */
    val tasks: List<UpgradeTaskViewData> = emptyList(),
    /** 筛选前可升级应用的总数。 */
    val availableCount: Int = 0,
    /** 筛选前的失败任务总数。 */
    val failedCount: Int = 0,
    /** 升级任务的聚合统计数据。 */
    val stats: TaskCenterStats = TaskCenterStats(),
    /** 当前选中的任务筛选条件。 */
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    /** 当前筛选范围内可直接执行的升级任务数。 */
    val batchRunnableCount: Int = 0,
    /** 当前是否需要展示失败面板。 */
    val showFailurePanel: Boolean = false,
    /** 升级中心扩展控制区的界面状态。 */
    val controlsUiState: UpgradeCenterControlsUiState = UpgradeCenterControlsUiState(),
    /** 当前显式页面状态机。 */
    val screenState: UpgradeScreenState = UpgradeScreenState.Loading,
)

/**
 * UpgradeScreenState 描述升级中心当前页面状态。
 */
sealed interface UpgradeScreenState {
    /** 页面正在同步升级任务。 */
    data object Loading : UpgradeScreenState

    /** 页面已有可展示内容。 */
    data object Content : UpgradeScreenState

    /** 页面暂无可展示内容。 */
    data object Empty : UpgradeScreenState

    /**
     * 页面加载失败。
     *
     * @property message 当前需要展示给用户的失败原因。
     */
    data class Error(
        /** 当前需要展示给用户的失败原因。 */
        val message: String,
    ) : UpgradeScreenState
}
