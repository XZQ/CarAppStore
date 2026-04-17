package com.nio.appstore.feature.detail

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.ModelText
import com.nio.appstore.domain.state.PrimaryAction

/**
 * DetailUiState 描述详情页需要渲染的全部界面状态。
 */
data class DetailUiState(
    /** 当前应用已加载的详情数据。 */
    val appDetail: AppDetail? = null,
    /** 当前页面显式状态。 */
    val screenState: DetailScreenState = DetailScreenState.Loading,
    /** 当前展示给用户的下载或安装状态文案。 */
    val stateText: String = ModelText.STATUS_NOT_INSTALLED,
    /** 当前状态文案对应的展示色调。 */
    val statusTone: StatusTone = StatusTone.NEUTRAL,
    /** 当前可执行的主动作。 */
    val primaryAction: PrimaryAction = PrimaryAction.DOWNLOAD,
    /** 当前聚合后的下载进度百分比。 */
    val progress: Int = 0,
    /** 当前策略提示文案。 */
    val policyPrompt: String = "",
)

/**
 * DetailScreenState 描述详情页当前所处的页面状态。
 */
sealed interface DetailScreenState {
    /** 详情页正在拉取远端信息。 */
    data object Loading : DetailScreenState

    /** 详情页已有内容。 */
    data object Content : DetailScreenState

    /**
     * 详情页加载失败。
     *
     * @property message 当前失败原因。
     */
    data class Error(
        /** 当前需要展示给用户的失败原因。 */
        val message: String,
    ) : DetailScreenState
}
