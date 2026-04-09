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
    fun actionHint(centerName: String, scope: String): String =
        "可在${centerName}统一管理筛选、$scope，并保持和其它任务中心一致的操作方式。"

    fun centerSummary(centerName: String, visibleCount: Int, totalCount: Int): String =
        "${centerName}当前展示 $visibleCount 个任务，全部任务共 $totalCount 个"

    fun emptyTitle(centerName: String): String = centerName + EMPTY_SUFFIX

    fun filteredEmptyTitle(centerName: String, filterLabel: String): String =
        "${centerName}在“$filterLabel”下暂无任务"

    fun emptyAll(centerName: String): String = EMPTY_ALL_TEMPLATE.format(centerName)

    fun emptyFailed(centerName: String): String = EMPTY_FAILED_TEMPLATE.format(centerName)

    // 摘要与统计文案
    fun subtitleEmpty(filterLabel: String): String =
        "$FILTER_PREFIX$filterLabel，暂无任务"

    fun subtitleSummary(
        filterLabel: String,
        primaryLabel: String,
        primaryCount: Int,
        secondarySummary: String,
        failedCount: Int,
    ): String = "$FILTER_PREFIX$filterLabel，$primaryLabel $primaryCount 个$secondarySummary，失败 $failedCount 个"

    fun secondarySummary(secondaryLabel: String, secondaryCount: Int): String =
        "，$secondaryLabel $secondaryCount 个"

    fun statsLine(
        prefix: String,
        active: Int,
        pending: Int,
        failed: Int,
        completed: Int,
    ): String =
        "${prefix}：执行中${active}${STATS_DIVIDER}待处理${pending}${STATS_DIVIDER}失败${failed}${STATS_DIVIDER}完成$completed"

    fun filterButtonText(filterLabel: String): String =
        FILTER_BUTTON_PREFIX + filterLabel

    fun headerHint(primary: String): String =
        "可在此统一查看${primary}统计、切换筛选并执行批量操作。"

    fun batchSummary(filterLabel: String, total: Int, failed: Int): String =
        "已加载 $filterLabel 范围内任务 $total 个，其中失败任务 $failed 个"

    fun compactStatTitle(label: String, count: Int): String =
        "$label\n$count"

    fun emptyPanelHint(centerName: String): String =
        "$centerName 会在任务创建、失败恢复和批量处理后自动刷新。"

    fun failurePanelTitle(centerName: String, failedCount: Int): String =
        "$centerName 当前有 $failedCount 个失败任务"

    fun failurePanelHint(centerName: String): String =
        "$centerName 的失败任务在处理完成后，会自动刷新统计区、列表区和空态展示。"

    fun batchActionSummary(runnableCount: Int, failedCount: Int): String =
        "可直接执行 $runnableCount 项，可清理失败态 $failedCount 项"

    fun sectionTitle(sectionName: String): String =
        sectionName

    fun sectionHint(sectionName: String): String =
        sectionName + SECTION_HINT_SUFFIX

    fun extensionTitle(centerName: String, title: String): String =
        "$centerName · $title"

    fun extensionHint(centerName: String, hint: String): String =
        "${centerName}的专属控制会放在这里，$hint"

    // 安装中心 session 相关文案
    fun sessionSummary(active: Int, failed: Int, recovered: Int): String =
        "Session 摘要：进行中 $active，失败 $failed，中断恢复 $recovered"

    fun sessionFilterLine(label: String, active: Int, failed: Int, recovered: Int): String =
        "｜$label｜会话 进行中 $active / 失败 $failed / 恢复 $recovered"

    fun switchSessionView(label: String): String =
        "切换 Session 视图（$label）"
}
