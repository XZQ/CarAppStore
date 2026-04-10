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

    /** 生成动作区标题。 */
    fun actionTitle(centerName: String): String = centerName + TaskCenterText.ACTION_AREA_SUFFIX

    /** 生成动作区提示文案。 */
    fun actionHint(centerName: String, scope: String): String =
        TaskCenterText.actionHint(centerName, scope)

    /** 生成任务中心头部摘要。 */
    fun centerSummary(centerName: String, visibleCount: Int, totalCount: Int): String =
        TaskCenterText.centerSummary(centerName, visibleCount, totalCount)

    /** 生成任务中心空态标题。 */
    fun emptyTitle(centerName: String, filter: TaskCenterFilter): String {
        return when (filter) {
            TaskCenterFilter.ALL -> TaskCenterText.emptyTitle(centerName)
            else -> TaskCenterText.filteredEmptyTitle(centerName, filter.label)
        }
    }

    /** 生成任务中心空态描述。 */
    fun emptyMessage(centerName: String, filter: TaskCenterFilter): String {
        return when (filter) {
            TaskCenterFilter.ALL -> TaskCenterText.emptyAll(centerName)
            TaskCenterFilter.FAILED -> TaskCenterText.emptyFailed(centerName)
            TaskCenterFilter.ACTIVE -> TaskCenterText.EMPTY_ACTIVE
            TaskCenterFilter.PENDING -> TaskCenterText.EMPTY_PENDING
            TaskCenterFilter.COMPLETED -> TaskCenterText.EMPTY_COMPLETED
        }
    }

    /** 生成头部副标题。 */
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

        // 没有统计项时显示空态副标题，否则展示统计型副标题。
        return if (primaryCount == 0 && secondaryCount == 0) {
            TaskCenterText.subtitleEmpty(filter.label)
        } else {
            TaskCenterText.subtitleSummary(filter.label, primaryLabel, primaryCount, secondary, failedCount)
        }
    }

    /** 生成统计行文案。 */
    fun statsLine(prefix: String, stats: TaskCenterStats): String =
        TaskCenterText.statsLine(prefix, stats.activeCount, stats.pendingCount, stats.failedCount, stats.completedCount)

    /** 生成筛选按钮文案。 */
    fun filterButtonText(filter: TaskCenterFilter): String =
        TaskCenterText.filterButtonText(filter.label)

    /** 生成头部提示文案。 */
    fun headerHint(primary: String): String =
        TaskCenterText.headerHint(primary)

    /** 生成批量摘要文案。 */
    fun batchSummary(filter: TaskCenterFilter, total: Int, failed: Int): String =
        TaskCenterText.batchSummary(filter.label, total, failed)

    /** 生成紧凑统计标题。 */
    fun compactStatTitle(label: String, count: Int): String =
        TaskCenterText.compactStatTitle(label, count)

    /** 生成空态面板提示。 */
    fun emptyPanelHint(centerName: String): String =
        TaskCenterText.emptyPanelHint(centerName)

    /** 生成失败面板标题。 */
    fun failurePanelTitle(centerName: String, failedCount: Int): String =
        TaskCenterText.failurePanelTitle(centerName, failedCount)

    /** 生成失败面板描述。 */
    fun failurePanelMessage(centerName: String): String =
        TaskCenterText.FAILURE_MESSAGE

    /** 生成失败面板提示。 */
    fun failurePanelHint(centerName: String): String =
        TaskCenterText.failurePanelHint(centerName)

    /** 生成批量动作摘要。 */
    fun batchActionSummary(runnableCount: Int, failedCount: Int): String =
        TaskCenterText.batchActionSummary(runnableCount, failedCount)

    /** 生成列表区块标题。 */
    fun sectionTitle(sectionName: String): String =
        TaskCenterText.sectionTitle(sectionName)

    /** 生成列表区块提示。 */
    fun sectionHint(sectionName: String): String =
        TaskCenterText.sectionHint(sectionName)

    /** 生成扩展区标题。 */
    fun extensionTitle(centerName: String, title: String): String =
        TaskCenterText.extensionTitle(centerName, title)

    /** 生成扩展区提示。 */
    fun extensionHint(centerName: String, hint: String): String =
        TaskCenterText.extensionHint(centerName, hint)
}
