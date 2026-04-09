package com.nio.appstore.data.local.entity

data class DownloadArtifactRefEntity(
    val appId: String,
    val apkPath: String,
    val fileSize: Long = 0L,
    val fileExists: Boolean = false,
    val updatedAt: Long,
)
