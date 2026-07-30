package com.xzq.appstore.core.policy

/**
 * PolicyRuntimeSignals 描述来自系统或 OEM 的实时策略信号。
 */
data class PolicyRuntimeSignals(
    /** 当前是否连接到 Wi‑Fi。 */
    val wifiConnected: Boolean = true,
    /** 当前车辆是否处于驻车状态。 */
    val parkingMode: Boolean = false,
    /** 当前设备是否处于低存储状态。 */
    val lowStorageMode: Boolean = false,
    /** 当前平台是否启用了车载安装限制；通用平台默认不启用。 */
    val vehicleInstallPolicyEnabled: Boolean = false,
)
