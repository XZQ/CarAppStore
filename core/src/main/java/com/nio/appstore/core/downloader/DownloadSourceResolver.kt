package com.nio.appstore.core.downloader

data class DownloadSourceResolverConfig(
    val defaultSourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
    val allowMockSource: Boolean = true,
    val allowDirectHttp: Boolean = true,
)

class DownloadSourceResolver(
    private val config: DownloadSourceResolverConfig,
) {
    fun resolve(
        requestedPolicy: DownloadSourcePolicy?,
        url: String,
    ): DownloadSourceDecision {
        val effective = requestedPolicy ?: config.defaultSourcePolicy
        return when (effective) {
            DownloadSourcePolicy.DIRECT_HTTP -> {
                if (!config.allowDirectHttp) {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, "当前环境禁用 DIRECT_HTTP，回退模拟下载")
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    DownloadSourceDecision(DownloadSourcePolicy.DIRECT_HTTP, "当前环境允许 DIRECT_HTTP")
                } else {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, "下载 URL 协议不受支持，回退模拟下载")
                }
            }

            DownloadSourcePolicy.MOCK -> {
                if (config.allowMockSource) {
                    DownloadSourceDecision(DownloadSourcePolicy.MOCK, "当前环境允许 MOCK 下载源")
                } else {
                    DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, "当前环境禁用 MOCK，回退模拟下载")
                }
            }

            DownloadSourcePolicy.FALLBACK_SIMULATED -> {
                DownloadSourceDecision(DownloadSourcePolicy.FALLBACK_SIMULATED, "当前策略指定回退模拟下载")
            }
        }
    }
}
