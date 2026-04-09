package com.nio.appstore.core.downloader

data class DownloadSourceResolverConfig(
    /** 请求未声明下载源策略时使用的默认兜底策略。 */
    val defaultSourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
    /** 当前环境是否允许模拟下载源。 */
    val allowMockSource: Boolean = true,
    /** 当前环境是否允许直连下载源。 */
    val allowDirectHttp: Boolean = true,
)

class DownloadSourceResolver(private val config: DownloadSourceResolverConfig) {
    fun resolve(
        requestedPolicy: DownloadSourcePolicy?,
        url: String,
    ): DownloadSourceDecision {
        val effective = requestedPolicy ?: config.defaultSourcePolicy
        return when (effective) {
            DownloadSourcePolicy.DIRECT_HTTP -> {
                if (!config.allowDirectHttp) {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, DownloaderText.DIRECT_HTTP_DISABLED)
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    DownloadSourceDecision(DownloadSourcePolicy.DIRECT_HTTP, DownloaderText.DIRECT_HTTP_ENABLED)
                } else {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, DownloaderText.UNSUPPORTED_URL_PROTOCOL)
                }
            }

            DownloadSourcePolicy.MOCK -> {
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
