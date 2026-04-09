package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

data class AppViewData(
    val appId: String,
    val name: String,
    val description: String,
    val versionName: String,
    val packageName: String? = null,
    val stateText: String,
    val statusTone: StatusTone = StatusTone.NEUTRAL,
    val primaryAction: PrimaryAction,
    val progress: Int = 0,
    val installed: Boolean = false,
)
