package com.xzq.appstore.domain.policy

import com.xzq.appstore.core.policy.PolicyRuntimeSignals
import com.xzq.appstore.data.model.PolicySettings

/**
 * 合并手动策略设置与系统实时策略信号。
 */
internal fun mergePolicySettings(
    /** 用户手动配置的持久化策略。 */
    stored: PolicySettings,
    /** 系统或 OEM 提供的实时信号。 */
    runtime: PolicyRuntimeSignals,
): PolicySettings {
    return PolicySettings(
        wifiConnected = stored.wifiConnected && runtime.wifiConnected,
        parkingMode = if (runtime.vehicleInstallPolicyEnabled) stored.parkingMode && runtime.parkingMode else true,
        lowStorageMode = stored.lowStorageMode || runtime.lowStorageMode,
        vehicleInstallPolicyEnabled = runtime.vehicleInstallPolicyEnabled,
    )
}
