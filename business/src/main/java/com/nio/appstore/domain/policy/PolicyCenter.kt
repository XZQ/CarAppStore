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
    val allow: Boolean,
    val reason: String = "",
)
