package com.nio.appstore.data.model

enum class TaskOverallStatus {
    /** 待处理任务。 */
    PENDING,
    /** 执行中任务。 */
    ACTIVE,
    /** 失败任务。 */
    FAILED,
    /** 已完成任务。 */
    COMPLETED,
}

enum class TaskCenterFilter(
    /** 展示给用户的任务筛选标签。 */
    val label: String,
) {
    ALL(ModelText.FILTER_ALL_TASKS),
    PENDING(ModelText.FILTER_PENDING),
    ACTIVE(ModelText.FILTER_ACTIVE),
    FAILED(ModelText.FILTER_FAILED),
    COMPLETED(ModelText.FILTER_COMPLETED);

    /** 获取下一个任务筛选项。 */
    fun next(): TaskCenterFilter = when (this) {
        ALL -> PENDING
        PENDING -> ACTIVE
        ACTIVE -> FAILED
        FAILED -> COMPLETED
        COMPLETED -> ALL
    }

    /** 判断当前筛选项是否匹配目标任务分组。 */
    fun matches(status: TaskOverallStatus): Boolean = when (this) {
        ALL -> true
        PENDING -> status == TaskOverallStatus.PENDING
        ACTIVE -> status == TaskOverallStatus.ACTIVE
        FAILED -> status == TaskOverallStatus.FAILED
        COMPLETED -> status == TaskOverallStatus.COMPLETED
    }
}

data class TaskCenterStats(
    /** 执行中任务数量。 */
    val activeCount: Int = 0,
    /** 待处理任务数量。 */
    val pendingCount: Int = 0,
    /** 失败任务数量。 */
    val failedCount: Int = 0,
    /** 已完成任务数量。 */
    val completedCount: Int = 0,
) {
    /** 当前统计项的总任务数。 */
    val totalCount: Int get() = activeCount + pendingCount + failedCount + completedCount
}

data class TaskCenterActionUiState(
    /** 操作区展示的中心名称。 */
    val centerName: String,
    /** 操作区标题下方展示的范围提示。 */
    val scopeHint: String,
    /** 当前选中的任务筛选条件。 */
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    /** 第二按钮文案。 */
    val secondaryText: String,
    /** 第三按钮文案。 */
    val tertiaryText: String,
    /** 可选的第四按钮文案。 */
    val quaternaryText: String? = null,
    /** 当前可执行任务数量。 */
    val runnableCount: Int = 0,
    /** 当前失败任务数量。 */
    val failedCount: Int = 0,
)

data class TaskCenterFailureUiState(
    /** 失败面板展示的中心名称。 */
    val centerName: String,
    /** 当前失败任务数量。 */
    val failedCount: Int,
    /** 主动作按钮文案。 */
    val primaryText: String,
    /** 次动作按钮文案。 */
    val secondaryText: String,
    /** 失败面板是否展示。 */
    val showPanel: Boolean,
    /** 是否展示次动作按钮。 */
    val showSecondary: Boolean = true,
)

data class TaskCenterEmptyUiState(
    /** 空态面板展示的中心名称。 */
    val centerName: String,
    /** 当前选中的筛选条件。 */
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    /** 主动作按钮文案。 */
    val primaryText: String,
    /** 次动作按钮文案。 */
    val secondaryText: String,
    /** 空态面板是否展示。 */
    val showEmpty: Boolean,
    /** 是否展示次动作按钮。 */
    val showSecondary: Boolean = true,
)


data class TaskCenterExtensionUiState(
    /** 用于拼装扩展区标题的中心名称。 */
    val centerName: String,
    /** 扩展面板标题。 */
    val title: String,
    /** 扩展面板提示文案。 */
    val hint: String,
    /** 扩展面板是否展示。 */
    val showPanel: Boolean = false,
)
