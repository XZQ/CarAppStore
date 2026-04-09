package com.nio.appstore.feature.myapp

import com.nio.appstore.data.model.AppViewData

data class MyAppUiState(
    /** 页面当前展示的应用或任务列表。 */
    val apps: List<AppViewData> = emptyList(),
)
