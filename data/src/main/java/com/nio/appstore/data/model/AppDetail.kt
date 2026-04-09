package com.nio.appstore.data.model

import com.nio.appstore.core.downloader.DownloadSourcePolicy

data class AppDetail(
    val appId: String,
    val packageName: String,
    val name: String,
    val description: String,
    val versionName: String,
    val apkUrl: String,
    val checksumType: String? = null,
    val checksumValue: String? = null,
    val sourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
)
