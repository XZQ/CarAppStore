package com.nio.appstore.feature.home

import com.nio.appstore.data.model.AppViewData

data class HomeUiState(
    /** 页面是否仍在加载初始内容。 */
    val loading: Boolean = true,
    /** 页面当前展示的推荐应用列表。 */
    val apps: List<AppViewData> = emptyList(),
    /** 列表上方展示的策略提示。 */
    val policyPrompt: String = "",
)
