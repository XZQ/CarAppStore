package com.xzq.appstore.core.downloader

enum class DownloadSourcePolicy {
    /** 强制使用 mock 下载源。 */
    MOCK,

    /** 强制使用真实 HTTP 下载源。 */
    DIRECT_HTTP,

    /** 无法满足真实下载条件时回退模拟下载。 */
    FALLBACK_SIMULATED,

    /** 仅用于解析决策：真实下载不可用且环境禁止模拟，必须失败而不是产出假产物。 */
    REJECTED,
}

data class DownloadSourceDecision(
    /** 当前请求最终选中的下载源策略。 */
    val policy: DownloadSourcePolicy,
    /** 说明最终策略如何被选中的原因文案。 */
    val reason: String,
)
