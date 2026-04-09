package com.nio.appstore.data.model

data class DownloadPreferences(
    val autoResumeOnLaunch: Boolean = false,
    val autoRetryEnabled: Boolean = true,
    val maxAutoRetryCount: Int = 2,
)
