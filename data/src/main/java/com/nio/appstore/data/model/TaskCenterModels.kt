package com.nio.appstore.data.model

enum class TaskOverallStatus {
    PENDING,
    ACTIVE,
    FAILED,
    COMPLETED,
}

enum class TaskCenterFilter(val label: String) {
    ALL("全部任务"),
    PENDING("待处理"),
    ACTIVE("执行中"),
    FAILED("失败项"),
    COMPLETED("已完成");

    fun next(): TaskCenterFilter = when (this) {
        ALL -> PENDING
        PENDING -> ACTIVE
        ACTIVE -> FAILED
        FAILED -> COMPLETED
        COMPLETED -> ALL
    }

    fun matches(status: TaskOverallStatus): Boolean = when (this) {
        ALL -> true
        PENDING -> status == TaskOverallStatus.PENDING
        ACTIVE -> status == TaskOverallStatus.ACTIVE
        FAILED -> status == TaskOverallStatus.FAILED
        COMPLETED -> status == TaskOverallStatus.COMPLETED
    }
}

data class TaskCenterStats(
    val activeCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val completedCount: Int = 0,
) {
    val totalCount: Int get() = activeCount + pendingCount + failedCount + completedCount
}

data class TaskCenterActionUiState(
    val centerName: String,
    val scopeHint: String,
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    val secondaryText: String,
    val tertiaryText: String,
    val quaternaryText: String? = null,
    val runnableCount: Int = 0,
    val failedCount: Int = 0,
)

data class TaskCenterFailureUiState(
    val centerName: String,
    val failedCount: Int,
    val primaryText: String,
    val secondaryText: String,
    val showPanel: Boolean,
    val showSecondary: Boolean = true,
)

data class TaskCenterEmptyUiState(
    val centerName: String,
    val selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL,
    val primaryText: String,
    val secondaryText: String,
    val showEmpty: Boolean,
    val showSecondary: Boolean = true,
)


data class TaskCenterExtensionUiState(
    val centerName: String,
    val title: String,
    val hint: String,
    val showPanel: Boolean = false,
)
