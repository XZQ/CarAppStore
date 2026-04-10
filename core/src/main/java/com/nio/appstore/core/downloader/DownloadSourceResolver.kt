package com.nio.appstore.core.downloader

data class DownloadSourceResolverConfig(
    /** 请求未声明下载源策略时使用的默认兜底策略。 */
    val defaultSourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
    /** 当前环境是否允许模拟下载源。 */
    val allowMockSource: Boolean = true,
    /** 当前环境是否允许直连下载源。 */
    val allowDirectHttp: Boolean = true,
)

class DownloadSourceResolver(
    /** 当前环境允许使用的下载源能力配置。 */
    private val config: DownloadSourceResolverConfig,
) {
    /** 根据请求策略和环境能力决定实际要使用的下载源。 */
    fun resolve(
        requestedPolicy: DownloadSourcePolicy?,
        url: String,
    ): DownloadSourceDecision {
        val effective = requestedPolicy ?: config.defaultSourcePolicy
        return when (effective) {
            DownloadSourcePolicy.DIRECT_HTTP -> {
                // 直连下载先检查环境是否允许，再校验 URL 协议是否合法。
                if (!config.allowDirectHttp) {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, DownloaderText.DIRECT_HTTP_DISABLED)
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    DownloadSourceDecision(DownloadSourcePolicy.DIRECT_HTTP, DownloaderText.DIRECT_HTTP_ENABLED)
                } else {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, DownloaderText.UNSUPPORTED_URL_PROTOCOL)
                }
            }

            DownloadSourcePolicy.MOCK -> {
                // mock 下载源由环境开关显式控制。
                if (config.allowMockSource) {
                    DownloadSourceDecision(DownloadSourcePolicy.MOCK, DownloaderText.MOCK_ENABLED)
                } else {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, DownloaderText.MOCK_DISABLED)
                }
            }

            DownloadSourcePolicy.FALLBACK_SIMULATED ->
                DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, DownloaderText.FALLBACK_SIMULATED)
        }
    }
}
