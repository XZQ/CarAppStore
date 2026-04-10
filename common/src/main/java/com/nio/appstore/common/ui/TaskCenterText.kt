package com.nio.appstore.common.ui

/**
 * TaskCenterText 是任务中心公共模板文案的最终统一入口。
 *
 * M12 阶段正式确定的策略是：
 *
 * 1. **页面专属文案**（例如某个 Fragment 独有标题、按钮文案、局部提示）
 *    继续放进 `string.xml`。
 *
 * 2. **任务中心跨页面复用的公共模板文案**
 *    继续保留在 `TaskCenterText`。
 *
 * 这样做的原因是：
 * - 这些文案本身强依赖运行时参数拼装；
 * - 它们跨多个任务中心共用；
 * - 当前多 module 结构下，把这层保留在 common 更利于复用；
 * - 不需要让每个 feature 反复维护同一批模板资源。
 *
 * 因此，TaskCenterText 现在是“任务中心公共模板层”，不是过渡产物。
 */
object TaskCenterText {

    // 基础前后缀与分隔符
    const val ACTION_AREA_SUFFIX = "操作区"
    const val EMPTY_SUFFIX = "暂无任务"
    const val FILTER_PREFIX = "当前筛选："
    const val FILTER_BUTTON_PREFIX = "筛选："
    const val STATS_DIVIDER = " / "
    const val SECTION_HINT_SUFFIX = "会自动跟随当前筛选、失败恢复和批量操作结果同步刷新。"

    // 空态与失败态模板
    const val EMPTY_ALL_TEMPLATE = "先在首页或详情页发起任务，这里会统一展示 %s 的执行、失败和完成状态。"
    const val EMPTY_FAILED_TEMPLATE = "当前没有失败项，说明 %s 运行状态良好。"
    const val EMPTY_ACTIVE = "当前没有执行中的任务，可以稍后再来看。"
    const val EMPTY_PENDING = "当前没有待处理任务，可以切换其他筛选查看。"
    const val EMPTY_COMPLETED = "当前没有已完成任务，可以切换其他筛选查看。"
    const val FAILURE_MESSAGE = "可以先批量重试失败项；如果问题来自策略限制或安装包缺失，也可以先清理失败态，再回到下载管理或详情页重新发起。"

    // 公共说明文案
    private const val ACTION_HINT_TEMPLATE = "可在%s统一管理筛选、%s，并保持和其它任务中心一致的操作方式。"
    private const val CENTER_SUMMARY_TEMPLATE = "%s当前展示 %d 个任务，全部任务共 %d 个"
    private const val FILTERED_EMPTY_TITLE_TEMPLATE = "%s在“%s”下暂无任务"
    private const val SUBTITLE_EMPTY_TEMPLATE = "%s%s，暂无任务"
    private const val SUBTITLE_SUMMARY_TEMPLATE = "%s%s，%s %d 个%s，失败 %d 个"
    private const val SECONDARY_SUMMARY_TEMPLATE = "，%s %d 个"
    private const val STATS_LINE_TEMPLATE = "%s：执行中%d%s待处理%d%s失败%d%s完成%d"
    private const val HEADER_HINT_TEMPLATE = "可在此统一查看%s统计、切换筛选并执行批量操作。"
    private const val BATCH_SUMMARY_TEMPLATE = "已加载 %s 范围内任务 %d 个，其中失败任务 %d 个"
    private const val EMPTY_PANEL_HINT_TEMPLATE = "%s 会在任务创建、失败恢复和批量处理后自动刷新。"
    private const val FAILURE_PANEL_TITLE_TEMPLATE = "%s 当前有 %d 个失败任务"
    private const val FAILURE_PANEL_HINT_TEMPLATE = "%s 的失败任务在处理完成后，会自动刷新统计区、列表区和空态展示。"
    private const val BATCH_ACTION_SUMMARY_TEMPLATE = "可直接执行 %d 项，可清理失败态 %d 项"
    private const val EXTENSION_TITLE_TEMPLATE = "%s · %s"
    private const val EXTENSION_HINT_TEMPLATE = "%s的专属控制会放在这里，%s"
    private const val SESSION_SUMMARY_TEMPLATE = "Session 摘要：进行中 %d，失败 %d，中断恢复 %d"
    private const val SESSION_FILTER_LINE_TEMPLATE = "｜%s｜会话 进行中 %d / 失败 %d / 恢复 %d"
    private const val SWITCH_SESSION_VIEW_TEMPLATE = "切换 Session 视图（%s）"

    /** 生成动作区提示文案。 */
    fun actionHint(centerName: String, scope: String): String = ACTION_HINT_TEMPLATE.format(centerName, scope)

    /** 生成任务中心头部统计摘要。 */
    fun centerSummary(centerName: String, visibleCount: Int, totalCount: Int): String =
        CENTER_SUMMARY_TEMPLATE.format(centerName, visibleCount, totalCount)

    /** 生成“全部为空”场景的标题。 */
    fun emptyTitle(centerName: String): String = centerName + EMPTY_SUFFIX

    /** 生成带筛选条件的空态标题。 */
    fun filteredEmptyTitle(centerName: String, filterLabel: String): String = FILTERED_EMPTY_TITLE_TEMPLATE.format(centerName, filterLabel)

    /** 生成全量空态描述。 */
    fun emptyAll(centerName: String): String = EMPTY_ALL_TEMPLATE.format(centerName)

    /** 生成失败筛选空态描述。 */
    fun emptyFailed(centerName: String): String = EMPTY_FAILED_TEMPLATE.format(centerName)

    // 摘要与统计文案
    /** 生成没有任务时的副标题。 */
    fun subtitleEmpty(filterLabel: String): String = SUBTITLE_EMPTY_TEMPLATE.format(FILTER_PREFIX, filterLabel)

    /** 生成带主次统计项的副标题。 */
    fun subtitleSummary(
        filterLabel: String,
        primaryLabel: String,
        primaryCount: Int,
        secondarySummary: String,
        failedCount: Int,
    ): String = SUBTITLE_SUMMARY_TEMPLATE.format(FILTER_PREFIX, filterLabel, primaryLabel, primaryCount, secondarySummary, failedCount)

    /** 生成副统计项摘要。 */
    fun secondarySummary(secondaryLabel: String, secondaryCount: Int): String = SECONDARY_SUMMARY_TEMPLATE.format(secondaryLabel, secondaryCount)

    /** 生成统计行文案。 */
    fun statsLine(
        prefix: String,
        active: Int,
        pending: Int,
        failed: Int,
        completed: Int,
    ): String = STATS_LINE_TEMPLATE.format(prefix, active, STATS_DIVIDER, pending, STATS_DIVIDER, failed, STATS_DIVIDER, completed)

    /** 生成筛选按钮文案。 */
    fun filterButtonText(filterLabel: String): String =
        FILTER_BUTTON_PREFIX + filterLabel

    /** 生成头部提示文案。 */
    fun headerHint(primary: String): String = HEADER_HINT_TEMPLATE.format(primary)

    /** 生成批量操作摘要。 */
    fun batchSummary(filterLabel: String, total: Int, failed: Int): String = BATCH_SUMMARY_TEMPLATE.format(filterLabel, total, failed)

    /** 生成紧凑统计标题。 */
    fun compactStatTitle(label: String, count: Int): String =
        "$label\n$count"

    /** 生成空态面板提示文案。 */
    fun emptyPanelHint(centerName: String): String = EMPTY_PANEL_HINT_TEMPLATE.format(centerName)

    /** 生成失败面板标题。 */
    fun failurePanelTitle(centerName: String, failedCount: Int): String = FAILURE_PANEL_TITLE_TEMPLATE.format(centerName, failedCount)

    /** 生成失败面板提示文案。 */
    fun failurePanelHint(centerName: String): String = FAILURE_PANEL_HINT_TEMPLATE.format(centerName)

    /** 生成批量动作统计摘要。 */
    fun batchActionSummary(runnableCount: Int, failedCount: Int): String = BATCH_ACTION_SUMMARY_TEMPLATE.format(runnableCount, failedCount)

    /** 生成列表区块标题。 */
    fun sectionTitle(sectionName: String): String =
        sectionName

    /** 生成列表区块提示文案。 */
    fun sectionHint(sectionName: String): String =
        sectionName + SECTION_HINT_SUFFIX

    /** 生成扩展区标题。 */
    fun extensionTitle(centerName: String, title: String): String = EXTENSION_TITLE_TEMPLATE.format(centerName, title)

    /** 生成扩展区提示文案。 */
    fun extensionHint(centerName: String, hint: String): String = EXTENSION_HINT_TEMPLATE.format(centerName, hint)

    // 安装中心会话相关文案
    /** 生成安装会话摘要。 */
    fun sessionSummary(active: Int, failed: Int, recovered: Int): String = SESSION_SUMMARY_TEMPLATE.format(active, failed, recovered)

    /** 生成安装会话筛选摘要行。 */
    fun sessionFilterLine(label: String, active: Int, failed: Int, recovered: Int): String =
        SESSION_FILTER_LINE_TEMPLATE.format(label, active, failed, recovered)

    /** 生成切换 Session 视图的按钮文案。 */
    fun switchSessionView(label: String): String = SWITCH_SESSION_VIEW_TEMPLATE.format(label)
}
