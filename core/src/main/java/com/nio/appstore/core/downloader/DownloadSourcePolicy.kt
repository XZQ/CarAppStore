package com.nio.appstore.core.downloader

enum class DownloadSourcePolicy {
    /** 强制使用 mock 下载源。 */
    MOCK,
    /** 强制使用真实 HTTP 下载源。 */
    DIRECT_HTTP,
    /** 无法满足真实下载条件时回退模拟下载。 */
    FALLBACK_SIMULATED,
}

data class DownloadSourceDecision(
    /** 当前请求最终选中的下载源策略。 */
    val policy: DownloadSourcePolicy,
    /** 说明最终策略如何被选中的原因文案。 */
    val reason: String,
)
