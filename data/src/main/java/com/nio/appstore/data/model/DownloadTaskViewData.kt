package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

data class DownloadTaskViewData(
    val appId: String,
    val name: String,
    val versionName: String,
    val stateText: String,
    val statusTone: StatusTone,
    val overallStatus: TaskOverallStatus,
    val progress: Int,
    val primaryAction: PrimaryAction,
    val sizeText: String,
    val speedText: String,
    val timeText: String,
    val pathText: String,
    val secondaryActionText: String,
    val showSecondaryAction: Boolean,
    val installed: Boolean,
    val reasonText: String? = null,
    val updatedAt: Long = 0L,
)
