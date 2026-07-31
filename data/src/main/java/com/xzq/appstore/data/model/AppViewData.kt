package com.xzq.appstore.data.model

import com.xzq.appstore.common.ui.StatusTone
import com.xzq.appstore.domain.state.PrimaryAction

/**
 * AppViewData 是页面直接消费的应用卡片展示模型。
 */
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
    /** 应用声明支持的软件平台。 */
    val supportedPlatforms: Set<AppPlatform> = setOf(AppPlatform.ANDROID),
    /** 当前客户端平台。 */
    val currentPlatform: AppPlatform = AppPlatform.ANDROID,
    val iconText: String = "",
    val heroText: String = "",
    val iconUrl: String = "",
    val bannerUrl: String = "",
    val screenshotUrls: List<String> = emptyList(),
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
) {
    /** 当前客户端是否能直接下载并安装该应用。 */
    val currentPlatformSupported: Boolean
        get() = currentPlatform in supportedPlatforms
}
