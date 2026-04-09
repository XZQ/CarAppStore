package com.nio.appstore.data.local.entity

data class InstalledAppEntity(
    val appId: String,
    val packageName: String,
    val name: String,
    val versionName: String,
)
