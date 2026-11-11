package com.xzq.appstore.core.downloader

data class DownloadSourceResolverConfig(
    /** 请求未声明下载源策略时使用的默认兜底策略。 */
    val defaultSourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
    /** 当前环境是否允许模拟下载源。 */
    val allowMockSource: Boolean = true,
    /** 当前环境是否允许直连下载源。 */
    val allowDirectHttp: Boolean = true,
)

class DownloadSourceResolver(private val config: DownloadSourceResolverConfig) {
    /** 根据请求策略和环境能力决定实际要使用的下载源。 */
    fun resolve(requestedPolicy: DownloadSourcePolicy?, url: String): DownloadSourceDecision {
        val effective = requestedPolicy ?: config.defaultSourcePolicy
        return when (effective) {
            DownloadSourcePolicy.DIRECT_HTTP -> {
                // 直连下载先检查环境是否允许，再校验 URL 协议是否合法。
                if (!config.allowDirectHttp) {
                    simulatedOrRejected(DownloaderText.DIRECT_HTTP_DISABLED)
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    DownloadSourceDecision(DownloadSourcePolicy.DIRECT_HTTP, DownloaderText.DIRECT_HTTP_ENABLED)
                } else {
                    simulatedOrRejected(DownloaderText.UNSUPPORTED_URL_PROTOCOL)
                }
            }

            DownloadSourcePolicy.MOCK -> {
                // mock 下载源由环境开关显式控制。
                if (config.allowMockSource) {
                    DownloadSourceDecision(DownloadSourcePolicy.MOCK, DownloaderText.MOCK_ENABLED)
                } else {
                    simulatedOrRejected(DownloaderText.MOCK_DISABLED)
                }
            }

            DownloadSourcePolicy.FALLBACK_SIMULATED -> simulatedOrRejected(DownloaderText.FALLBACK_SIMULATED)

            // 请求方显式声明拒绝（或上游已判定不可下载）时直接透传，不再尝试任何下载源。
            DownloadSourcePolicy.REJECTED -> DownloadSourceDecision(DownloadSourcePolicy.REJECTED, DownloaderText.SOURCE_REJECTED)
        }
    }

    /**
     * 模拟下载兜底必须受环境开关约束：生产环境（allowMockSource=false）禁止模拟，
     * 否则目录漏写 sourcePolicy 或 URL 非法时会产出假 APK 并报「下载成功」，到安装才失败。
     * 允许模拟时回退模拟源；禁止时返回拒绝决策，由下载器收敛为失败。
     */
    private fun simulatedOrRejected(reason: String): DownloadSourceDecision =
        if (config.allowMockSource) {
            DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, reason)
        } else {
            DownloadSourceDecision(DownloadSourcePolicy.REJECTED, reason)
        }
}
