package com.nio.appstore.core.downloader

enum class DownloadSourcePolicy {
    MOCK,
    DIRECT_HTTP,
    FALLBACK_SIMULATED,
}

data class DownloadSourceDecision(
    /** 当前请求最终选中的下载源策略。 */
    val policy: DownloadSourcePolicy,
    /** 说明最终策略如何被选中的原因文案。 */
    val reason: String,
)
