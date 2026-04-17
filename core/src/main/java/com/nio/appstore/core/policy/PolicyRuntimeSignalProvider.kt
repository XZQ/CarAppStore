package com.nio.appstore.core.policy

import kotlinx.coroutines.flow.StateFlow

/**
 * PolicyRuntimeSignalProvider 定义系统/OEM 实时策略信号提供者。
 */
interface PolicyRuntimeSignalProvider {
    /** 观察实时策略信号。 */
    fun observeSignals(): StateFlow<PolicyRuntimeSignals>

    /** 读取当前实时策略信号快照。 */
    fun currentSignals(): PolicyRuntimeSignals
}
