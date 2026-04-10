package com.nio.appstore.data.model

/**
 * PolicySettings 描述策略中心持久化的开关状态。
 */
data class PolicySettings(
    /** 当前网络条件是否为无线网络。 */
    val wifiConnected: Boolean = true,
    /** 当前车辆是否处于驻车状态。 */
    val parkingMode: Boolean = true,
    /** 当前设备是否处于低存储状态。 */
    val lowStorageMode: Boolean = false,
)
