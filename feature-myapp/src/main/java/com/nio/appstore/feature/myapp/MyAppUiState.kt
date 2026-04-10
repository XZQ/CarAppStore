package com.nio.appstore.feature.myapp

import com.nio.appstore.data.model.AppViewData

/**
 * MyAppUiState 描述我的应用页需要渲染的界面状态。
 */
data class MyAppUiState(
    /** 页面当前展示的应用或任务列表。 */
    val apps: List<AppViewData> = emptyList(),
)
