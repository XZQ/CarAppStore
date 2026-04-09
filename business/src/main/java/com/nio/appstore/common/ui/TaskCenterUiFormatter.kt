package com.nio.appstore.common.ui

import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats

/**
 * TaskCenterUiFormatter 负责统一生成任务中心公共区域使用的文案。
 *
 * M12 阶段正式确定：
 * - 页面专属文案继续使用 string.xml；
 * - 任务中心跨页面复用模板继续使用 TaskCenterText。
 *
 * 因此 formatter 现在主要负责参数组合，而公共模板则交给 TaskCenterText。
 */
object TaskCenterUiFormatter {

    fun actionTitle(centerName: String): String = centerName + TaskCenterText.ACTION_AREA_SUFFIX

    fun actionHint(centerName: String, scope: String): String =
        TaskCenterText.actionHint(centerName, scope)

    fun centerSummary(centerName: String, visibleCount: Int, totalCount: Int): String =
        TaskCenterText.centerSummary(centerName, visibleCount, totalCount)

    fun emptyTitle(centerName: String, filter: TaskCenterFilter): String {
        return when (filter) {
            TaskCenterFilter.ALL -> TaskCenterText.emptyTitle(centerName)
            else -> TaskCenterText.filteredEmptyTitle(centerName, filter.label)
        }
    }

    fun emptyMessage(centerName: String, filter: TaskCenterFilter): String {
        return when (filter) {
            TaskCenterFilter.ALL -> TaskCenterText.emptyAll(centerName)
            TaskCenterFilter.FAILED -> TaskCenterText.emptyFailed(centerName)
            TaskCenterFilter.ACTIVE -> TaskCenterText.EMPTY_ACTIVE
            TaskCenterFilter.PENDING -> TaskCenterText.EMPTY_PENDING
            TaskCenterFilter.COMPLETED -> TaskCenterText.EMPTY_COMPLETED
        }
    }

    fun subtitle(
        filter: TaskCenterFilter,
        primaryLabel: String,
        primaryCount: Int,
        secondaryLabel: String? = null,
        secondaryCount: Int = 0,
        failedCount: Int = 0,
    ): String {
        val secondary = if (secondaryLabel.isNullOrBlank()) {
            ""
        } else {
            TaskCenterText.secondarySummary(secondaryLabel, secondaryCount)
        }

        return if (primaryCount == 0 && secondaryCount == 0) {
            TaskCenterText.subtitleEmpty(filter.label)
        } else {
            TaskCenterText.subtitleSummary(filter.label, primaryLabel, primaryCount, secondary, failedCount)
        }
    }

    fun statsLine(prefix: String, stats: TaskCenterStats): String =
        TaskCenterText.statsLine(prefix, stats.activeCount, stats.pendingCount, stats.failedCount, stats.completedCount)

    fun filterButtonText(filter: TaskCenterFilter): String =
        TaskCenterText.filterButtonText(filter.label)

    fun headerHint(primary: String): String =
        TaskCenterText.headerHint(primary)

    fun batchSummary(filter: TaskCenterFilter, total: Int, failed: Int): String =
        TaskCenterText.batchSummary(filter.label, total, failed)

    fun compactStatTitle(label: String, count: Int): String =
        TaskCenterText.compactStatTitle(label, count)

    fun emptyPanelHint(centerName: String): String =
        TaskCenterText.emptyPanelHint(centerName)

    fun failurePanelTitle(centerName: String, failedCount: Int): String =
        TaskCenterText.failurePanelTitle(centerName, failedCount)

    fun failurePanelMessage(centerName: String): String =
        TaskCenterText.FAILURE_MESSAGE

    fun failurePanelHint(centerName: String): String =
        TaskCenterText.failurePanelHint(centerName)

    fun batchActionSummary(runnableCount: Int, failedCount: Int): String =
        TaskCenterText.batchActionSummary(runnableCount, failedCount)

    fun sectionTitle(sectionName: String): String =
        TaskCenterText.sectionTitle(sectionName)

    fun sectionHint(sectionName: String): String =
        TaskCenterText.sectionHint(sectionName)

    fun extensionTitle(centerName: String, title: String): String =
        TaskCenterText.extensionTitle(centerName, title)

    fun extensionHint(centerName: String, hint: String): String =
        TaskCenterText.extensionHint(centerName, hint)
}
