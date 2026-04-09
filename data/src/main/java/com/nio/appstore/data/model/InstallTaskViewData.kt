package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

data class InstallTaskViewData(
    val appId: String,
    val packageName: String,
    val name: String,
    val versionName: String,
    val stateText: String,
    val statusTone: StatusTone,
    val overallStatus: TaskOverallStatus,
    val primaryAction: PrimaryAction,
    val reasonText: String? = null,
    val updatedAt: Long = 0L,
    val sessionIdText: String? = null,
    val sessionPhaseText: String? = null,
    val sessionProgressText: String? = null,
    val sessionBucket: SessionBucket = SessionBucket.NONE,
)

enum class SessionBucket {
    NONE,
    ACTIVE,
    FAILED,
    RECOVERED,
    COMPLETED,
}
