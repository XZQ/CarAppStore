package com.nio.appstore.data.model

data class PolicySettings(
    val wifiConnected: Boolean = true,
    val parkingMode: Boolean = true,
    val lowStorageMode: Boolean = false,
)
