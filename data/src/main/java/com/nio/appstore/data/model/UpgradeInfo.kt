package com.nio.appstore.data.model

data class UpgradeInfo(
    val appId: String,
    val latestVersion: String,
    val apkUrl: String,
    val hasUpgrade: Boolean,
)
