package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

/**
 * DownloadTaskViewData 是下载中心列表直接消费的展示模型。
 */
data class DownloadTaskViewData(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 展示给用户的应用名称。 */
    val name: String,
    /** 任务行中展示的版本文案。 */
    val versionName: String,
    /** 展示给用户的状态文案。 */
    val stateText: String,
    /** 用于渲染状态文案的视觉色调。 */
    val statusTone: StatusTone,
    /** 用于筛选和样式控制的聚合任务分组。 */
    val overallStatus: TaskOverallStatus,
    /** 当前进度百分比。 */
    val progress: Int,
    /** 当前暴露给用户的主动作。 */
    val primaryAction: PrimaryAction,
    /** 格式化后的大小摘要文案。 */
    val sizeText: String,
    /** 格式化后的速度摘要文案。 */
    val speedText: String,
    /** 格式化后的更新时间文案。 */
    val timeText: String,
    /** 本地文件路径摘要文案。 */
    val pathText: String,
    /** 次动作按钮文案。 */
    val secondaryActionText: String,
    /** 是否展示次动作按钮。 */
    val showSecondaryAction: Boolean,
    /** 关联应用是否已经安装。 */
    val installed: Boolean,
    /** 可选的失败原因文案。 */
    val reasonText: String? = null,
    /** 用于排序的最后更新时间戳。 */
    val updatedAt: Long = 0L,
)
