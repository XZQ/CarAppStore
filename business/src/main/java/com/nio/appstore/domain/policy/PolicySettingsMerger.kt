package com.nio.appstore.domain.policy

import com.nio.appstore.core.policy.PolicyRuntimeSignals
import com.nio.appstore.data.model.PolicySettings

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
        parkingMode = stored.parkingMode && runtime.parkingMode,
        lowStorageMode = stored.lowStorageMode || runtime.lowStorageMode,
    )
}
