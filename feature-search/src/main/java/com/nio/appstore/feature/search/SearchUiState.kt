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
    /** 搜索页当前的显式状态机。 */
    val screenState: SearchScreenState = SearchScreenState.Idle,
)

/**
 * SearchScreenState 描述搜索页当前所处的页面状态。
 */
sealed interface SearchScreenState {
    /** 搜索页尚未输入关键词。 */
    data object Idle : SearchScreenState

    /** 搜索页正在刷新结果。 */
    data object Loading : SearchScreenState

    /** 搜索页已有结果。 */
    data object Content : SearchScreenState

    /** 搜索结果为空。 */
    data object Empty : SearchScreenState

    /**
     * 搜索页加载失败。
     *
     * @property message 当前失败原因。
     */
    data class Error(
        /** 当前需要展示给用户的失败原因。 */
        val message: String,
    ) : SearchScreenState
}
