package com.nio.appstore.domain.policy

import com.nio.appstore.data.model.PolicySettings

interface PolicyCenter {
    fun canDownload(appId: String): PolicyResult
    fun canInstall(appId: String): PolicyResult
    fun canUpgrade(appId: String): PolicyResult
    fun getSettings(): PolicySettings
    fun updateSettings(settings: PolicySettings)
}

data class PolicyResult(
    /** 当前策略判断是否允许执行目标动作。 */
    val allow: Boolean,
    /** 被策略拦截时展示给用户的原因文案。 */
    val reason: String = "",
)
