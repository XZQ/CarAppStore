package com.nio.appstore.domain.policy

import com.nio.appstore.data.model.PolicySettings
import kotlinx.coroutines.flow.StateFlow

interface PolicyCenter {
    /** 判断当前应用是否允许发起下载。 */
    fun canDownload(appId: String): PolicyResult
    /** 判断当前应用是否允许发起安装。 */
    fun canInstall(appId: String): PolicyResult
    /** 判断当前应用是否允许发起升级。 */
    fun canUpgrade(appId: String): PolicyResult
    /** 观察当前生效的策略设置。 */
    fun observeSettings(): StateFlow<PolicySettings>
    /** 读取当前生效的策略设置。 */
    fun getSettings(): PolicySettings
    /** 读取当前持久化的手动策略设置。 */
    fun getStoredSettings(): PolicySettings
    /** 更新并持久化策略设置。 */
    fun updateSettings(settings: PolicySettings)
}

data class PolicyResult(
    /** 当前策略判断是否允许执行目标动作。 */
    val allow: Boolean,
    /** 被策略拦截时展示给用户的原因文案。 */
    val reason: String = "",
)
