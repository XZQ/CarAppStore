package com.nio.appstore.feature.detail

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.domain.state.PrimaryAction

data class DetailUiState(
    val appDetail: AppDetail? = null,
    val stateText: String = "未安装",
    val statusTone: StatusTone = StatusTone.NEUTRAL,
    val primaryAction: PrimaryAction = PrimaryAction.DOWNLOAD,
    val progress: Int = 0,
)
