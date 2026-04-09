package com.nio.appstore.core.downloader

enum class DownloadSourcePolicy {
    MOCK,
    DIRECT_HTTP,
    FALLBACK_SIMULATED,
}

data class DownloadSourceDecision(
    val policy: DownloadSourcePolicy,
    val reason: String,
)
