package com.nio.appstore.feature.search

import com.nio.appstore.data.model.AppViewData

/**
 * SearchUiState 描述搜索页需要渲染的界面状态。
 */
data class SearchUiState(
    /** 当前搜索关键词。 */
    val keyword: String = "",
    /** 页面当前展示的搜索结果。 */
    val apps: List<AppViewData> = emptyList(),
    /** 列表上方展示的策略提示。 */
    val policyPrompt: String = "",
)
