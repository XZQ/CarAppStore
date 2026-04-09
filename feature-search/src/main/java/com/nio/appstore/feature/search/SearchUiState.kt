package com.nio.appstore.feature.search

import com.nio.appstore.data.model.AppViewData

data class SearchUiState(
    val keyword: String = "",
    val apps: List<AppViewData> = emptyList(),
    val policyPrompt: String = "",
)
