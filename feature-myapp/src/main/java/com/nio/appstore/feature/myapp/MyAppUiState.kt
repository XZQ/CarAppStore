package com.nio.appstore.feature.myapp

import com.nio.appstore.data.model.AppViewData

data class MyAppUiState(
    val apps: List<AppViewData> = emptyList(),
)
