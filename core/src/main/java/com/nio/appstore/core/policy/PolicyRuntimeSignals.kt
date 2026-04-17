package com.nio.appstore.core.policy

/**
 * PolicyRuntimeSignals 描述来自系统或 OEM 的实时策略信号。
 */
data class PolicyRuntimeSignals(
    /** 当前是否连接到 Wi‑Fi。 */
    val wifiConnected: Boolean = true,
    /** 当前车辆是否处于驻车状态。 */
    val parkingMode: Boolean = true,
    /** 当前设备是否处于低存储状态。 */
    val lowStorageMode: Boolean = false,
)
