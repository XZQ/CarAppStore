package com.nio.appstore.domain.policy

import android.content.Context
import com.nio.appstore.core.policy.PolicyRuntimeSignalProvider
import com.nio.appstore.data.datasource.local.AppLocalDataSource
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.domain.text.BusinessText
import com.nio.appstore.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DefaultPolicyCenter(
    /** 用于读取设备空间等系统级策略信息的应用上下文。 */
    private val context: Context,
    /** 用于读取和保存策略设置的本地数据源。 */
    private val localDataSource: AppLocalDataSource,
    /** 提供系统/OEM 的实时策略信号。 */
    runtimeSignalProvider: PolicyRuntimeSignalProvider,
    /** 用于记录系统策略监听过程中的异常。 */
    private val logger: AppLogger = AppLogger(),
) : PolicyCenter {
    /** 策略中心内部协程作用域。 */
    private val policyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 当前持久化策略设置流。 */
    private val storedSettingsFlow = MutableStateFlow(localDataSource.getPolicySettings())
    /** 当前合并后的生效策略流。 */
    private val settingsFlow: StateFlow<PolicySettings> = storedSettingsFlow
        .combine(runtimeSignalProvider.observeSignals()) { stored, runtime ->
            mergePolicySettings(stored, runtime)
        }
        .stateIn(
            scope = policyScope,
            started = SharingStarted.Eagerly,
            initialValue = storedSettingsFlow.value,
        )

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

    /** 观察当前持久化策略配置。 */
    override fun observeSettings(): StateFlow<PolicySettings> = settingsFlow

    /** 读取当前持久化的策略配置。 */
    override fun getSettings(): PolicySettings = settingsFlow.value

    /** 读取当前持久化的手动策略配置。 */
    override fun getStoredSettings(): PolicySettings = storedSettingsFlow.value

    /** 持久化策略配置变更。 */
    override fun updateSettings(settings: PolicySettings) {
        localDataSource.savePolicySettings(settings)
        storedSettingsFlow.value = settings
    }

    companion object {
        private const val MIN_REQUIRED_SPACE_BYTES = 8L * 1024L * 1024L
        private const val TAG = "DefaultPolicyCenter"
    }
}
