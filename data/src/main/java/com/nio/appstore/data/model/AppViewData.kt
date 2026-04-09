package com.nio.appstore.data.model

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.domain.state.PrimaryAction

data class AppViewData(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 展示给用户的应用名称。 */
    val name: String,
    /** 列表中展示的次级描述。 */
    val description: String,
    /** 界面中展示的版本文案。 */
    val versionName: String,
    /** 已知时使用的安卓包名。 */
    val packageName: String? = null,
    /** 展示给用户的状态文案。 */
    val stateText: String,
    /** 用于渲染状态文案的视觉色调。 */
    val statusTone: StatusTone = StatusTone.NEUTRAL,
    /** 当前卡片可执行的主动作。 */
    val primaryAction: PrimaryAction,
    /** 当前流程对应的进度百分比。 */
    val progress: Int = 0,
    /** 应用当前是否已安装。 */
    val installed: Boolean = false,
)
