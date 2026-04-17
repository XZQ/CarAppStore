package com.nio.appstore.feature.home

import com.nio.appstore.data.model.AppViewData

/**
 * HomeUiState 描述首页需要渲染的界面状态。
 */
data class HomeUiState(
    /** 页面是否仍在加载初始内容。 */
    val loading: Boolean = true,
    /** 页面当前展示的推荐应用列表。 */
    val apps: List<AppViewData> = emptyList(),
    /** 列表上方展示的策略提示。 */
    val policyPrompt: String = "",
    /** 页面当前的显式状态机。 */
    val screenState: HomeScreenState = HomeScreenState.Loading,
)

/**
 * HomeScreenState 描述首页当前所处的页面状态。
 */
sealed interface HomeScreenState {
    /** 首页正在拉取数据。 */
    data object Loading : HomeScreenState

    /** 首页已有可展示内容。 */
    data object Content : HomeScreenState

    /** 首页暂无可展示内容。 */
    data object Empty : HomeScreenState

    /**
     * 首页加载失败。
     *
     * @property message 当前需要展示给用户的失败原因。
     */
    data class Error(
        /** 当前需要展示给用户的失败原因。 */
        val message: String,
    ) : HomeScreenState
}
