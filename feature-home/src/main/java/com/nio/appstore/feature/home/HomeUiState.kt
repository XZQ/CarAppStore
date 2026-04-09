package com.nio.appstore.feature.home

import com.nio.appstore.data.model.AppViewData

data class HomeUiState(
    val loading: Boolean = true,
    val apps: List<AppViewData> = emptyList(),
    val policyPrompt: String = "",
)
