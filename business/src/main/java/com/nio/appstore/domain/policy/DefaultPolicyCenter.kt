package com.nio.appstore.domain.policy

import android.content.Context
import com.nio.appstore.data.datasource.local.AppLocalDataSource
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.domain.text.BusinessText

class DefaultPolicyCenter(
    /** 用于读取设备空间等系统级策略信息的应用上下文。 */
    private val context: Context,
    /** 用于读取和保存策略设置的本地数据源。 */
    private val localDataSource: AppLocalDataSource,
) : PolicyCenter {

    /** 校验下载链路是否满足网络和存储前置条件。 */
    override fun canDownload(appId: String): PolicyResult {
        val settings = getSettings()
        return when {
            !settings.wifiConnected -> PolicyResult(false, BusinessText.POLICY_NOT_WIFI)
            settings.lowStorageMode -> PolicyResult(false, BusinessText.POLICY_LOW_STORAGE)
            context.filesDir.usableSpace < MIN_REQUIRED_SPACE_BYTES -> PolicyResult(false, BusinessText.POLICY_DEVICE_STORAGE_LOW)
            else -> PolicyResult(true)
        }
    }

    /** 校验安装链路是否满足驻车和空间等前置条件。 */
    override fun canInstall(appId: String): PolicyResult {
        val settings = getSettings()
        return when {
            !settings.parkingMode -> PolicyResult(false, BusinessText.POLICY_NOT_PARKING)
            settings.lowStorageMode -> PolicyResult(false, BusinessText.POLICY_LOW_STORAGE)
            else -> PolicyResult(true)
        }
    }

    /** 升级同时依赖下载和安装两个策略校验结果。 */
    override fun canUpgrade(appId: String): PolicyResult {
        // 先复用下载策略，避免重复维护一套校验分支。
        val downloadPolicy = canDownload(appId)
        if (!downloadPolicy.allow) return downloadPolicy
        // 下载允许后再校验安装条件，保证升级链路两端都可执行。
        val installPolicy = canInstall(appId)
        if (!installPolicy.allow) return installPolicy
        return PolicyResult(true)
    }

    /** 读取当前持久化的策略配置。 */
    override fun getSettings(): PolicySettings = localDataSource.getPolicySettings()

    /** 持久化策略配置变更。 */
    override fun updateSettings(settings: PolicySettings) {
        localDataSource.savePolicySettings(settings)
    }

    companion object {
        private const val MIN_REQUIRED_SPACE_BYTES = 8L * 1024L * 1024L
    }
}
