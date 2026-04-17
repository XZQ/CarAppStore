package com.nio.appstore.feature.myapp

import com.nio.appstore.data.model.AppViewData

/**
 * MyAppUiState 描述我的应用页需要渲染的界面状态。
 */
data class MyAppUiState(
    /** 页面当前展示的应用或任务列表。 */
    val apps: List<AppViewData> = emptyList(),
    /** 页面当前显式状态机。 */
    val screenState: MyAppScreenState = MyAppScreenState.Loading,
)

/**
 * MyAppScreenState 描述“我的应用”页面所处的状态。
 */
sealed interface MyAppScreenState {
    /** 页面正在拉取“我的应用”列表。 */
    data object Loading : MyAppScreenState

    /** 页面已有可展示内容。 */
    data object Content : MyAppScreenState

    /** 页面暂无可展示内容。 */
    data object Empty : MyAppScreenState

    /**
     * 页面加载失败。
     *
     * @property message 当前需要展示给用户的失败原因。
     */
    data class Error(
        /** 当前需要展示给用户的失败原因。 */
        val message: String,
    ) : MyAppScreenState
}
