package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

/**
 * UpgradeTaskViewData 是升级中心列表直接消费的展示模型。
 */
data class UpgradeTaskViewData(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 安卓包名。 */
    val packageName: String,
    /** 展示给用户的应用名称。 */
    val name: String,
    /** 升级前的当前版本号。 */
    val currentVersion: String,
    /** 升级后期望达到的目标版本。 */
    val targetVersion: String,
    /** 展示给用户的状态文案。 */
    val stateText: String,
    /** 用于渲染状态文案的视觉色调。 */
    val statusTone: StatusTone,
    /** 用于筛选和样式控制的聚合任务分组。 */
    val overallStatus: TaskOverallStatus,
    /** 当前暴露给用户的主动作。 */
    val primaryAction: PrimaryAction,
    /** 可选的失败原因文案。 */
    val reasonText: String? = null,
    /** 用于排序的最后更新时间戳。 */
    val updatedAt: Long = 0L,
)
