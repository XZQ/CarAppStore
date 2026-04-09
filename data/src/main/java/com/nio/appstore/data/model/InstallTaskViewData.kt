package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

data class InstallTaskViewData(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 安卓包名。 */
    val packageName: String,
    /** 展示给用户的应用名称。 */
    val name: String,
    /** 当前展示的版本文案。 */
    val versionName: String,
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
    /** 格式化后的安装会话标识文案。 */
    val sessionIdText: String? = null,
    /** 格式化后的安装会话阶段文案。 */
    val sessionPhaseText: String? = null,
    /** 格式化后的安装会话进度文案。 */
    val sessionProgressText: String? = null,
    /** 安装中心会话筛选使用的分组。 */
    val sessionBucket: SessionBucket = SessionBucket.NONE,
)

enum class SessionBucket {
    NONE,
    ACTIVE,
    FAILED,
    RECOVERED,
    COMPLETED,
}
