package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

data class UpgradeTaskViewData(
    val appId: String,
    val packageName: String,
    val name: String,
    val currentVersion: String,
    val targetVersion: String,
    val stateText: String,
    val statusTone: StatusTone,
    val overallStatus: TaskOverallStatus,
    val primaryAction: PrimaryAction,
    val reasonText: String? = null,
    val updatedAt: Long = 0L,
)
