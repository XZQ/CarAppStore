package com.xzq.appstore.domain.policy

import com.xzq.appstore.data.model.PolicySettings
import kotlinx.coroutines.flow.StateFlow

interface PolicyCenter {
    /** 判断当前应用是否允许发起下载。 */
    fun canDownload(appId: String): PolicyResult

    /** 判断当前应用是否允许发起安装。 */
    fun canInstall(appId: String): PolicyResult

    /** 判断当前应用是否允许发起升级（要求同时满足下载与安装条件）。 */
    fun canUpgrade(appId: String): PolicyResult

    /**
     * 判断当前应用是否允许发起升级。
     *
     * 当 [apkAlreadyDownloaded] 为 true 时跳过下载链路校验（如 Wi-Fi），
     * 仅校验安装条件，避免在 APK 已落盘的情况下因无关条件误拦。
     */
    fun canUpgrade(appId: String, apkAlreadyDownloaded: Boolean): PolicyResult = if (apkAlreadyDownloaded) canInstall(appId) else canUpgrade(appId)

    /** 观察当前生效的策略设置。 */
    fun observeSettings(): StateFlow<PolicySettings>

    /** 读取当前生效的策略设置。 */
    fun getSettings(): PolicySettings

    /** 读取当前持久化的手动策略设置。 */
    fun getStoredSettings(): PolicySettings

    /** 更新并持久化策略设置。 */
    fun updateSettings(settings: PolicySettings)

    /** 释放策略中心持有的协程与监听。默认空实现便于测试与不持有资源的对象。 */
    fun close() {}
}

data class PolicyResult(
    /** 当前策略判断是否允许执行目标动作。 */
    val allow: Boolean,
    /** 被策略拦截时展示给用户的原因文案。 */
    val reason: String = "",
)
