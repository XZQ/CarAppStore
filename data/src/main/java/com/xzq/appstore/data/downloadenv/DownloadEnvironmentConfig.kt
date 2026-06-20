package com.xzq.appstore.data.downloadenv

import com.xzq.appstore.core.downloader.DownloadSourcePolicy
import com.xzq.appstore.data.BuildConfig

enum class DownloadEnvironment {
    /** 开发环境。 */
    DEV,
    /** 测试环境。 */
    TEST,
    /** 生产环境。 */
    PROD,
}

data class DownloadEnvironmentConfig(
    /** 当前选中的下载环境。 */
    val environment: DownloadEnvironment = DownloadEnvironment.DEV,
    /** 当前环境下默认采用的下载源策略。 */
    val defaultSourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
    /** 当前环境是否允许模拟下载源。 */
    val allowMockSource: Boolean = true,
    /** 当前环境是否允许直连下载源。 */
    val allowDirectHttp: Boolean = true,
    /** 当前环境下商店目录接口地址。 */
    val catalogEndpointUrl: String? = null,
    /** 当前环境下商店目录附加请求头。 */
    val catalogRequestHeaders: Map<String, String> = emptyMap(),
    /** 当前环境下 APK 下载基地址，用于构造未显式配置应用的默认下载 URL。 */
    val downloadBaseUrl: String = "https://example.com",
) {
    companion object {
        /** 根据下载环境生成对应的能力配置。 */
        fun forEnvironment(environment: DownloadEnvironment): DownloadEnvironmentConfig {
            return when (environment) {
                DownloadEnvironment.DEV -> DownloadEnvironmentConfig(
                    environment = environment,
                    defaultSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
                    allowMockSource = true,
                    allowDirectHttp = true,
                    catalogEndpointUrl = configuredCatalogUrl(BuildConfig.CARAPPSTORE_CATALOG_DEV_URL, null),
                    catalogRequestHeaders = configuredHeaders("carappstore-dev"),
                    downloadBaseUrl = configuredDownloadBaseUrl(BuildConfig.CARAPPSTORE_DOWNLOAD_DEV_BASE_URL, "https://example.com"),
                )
                DownloadEnvironment.TEST -> DownloadEnvironmentConfig(
                    environment = environment,
                    defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
                    allowMockSource = true,
                    allowDirectHttp = true,
                    catalogEndpointUrl = configuredCatalogUrl(
                        BuildConfig.CARAPPSTORE_CATALOG_TEST_URL,
                        "https://test.example.org/carappstore/catalog.json",
                    ),
                    catalogRequestHeaders = configuredHeaders("carappstore-test"),
                    downloadBaseUrl = configuredDownloadBaseUrl(
                        BuildConfig.CARAPPSTORE_DOWNLOAD_TEST_BASE_URL,
                        "https://test-download.example.org",
                    ),
                )
                DownloadEnvironment.PROD -> DownloadEnvironmentConfig(
                    environment = environment,
                    defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
                    allowMockSource = false,
                    allowDirectHttp = true,
                    catalogEndpointUrl = configuredCatalogUrl(
                        BuildConfig.CARAPPSTORE_CATALOG_PROD_URL,
                        "https://cdn.example.com/carappstore/catalog.json",
                    ),
                    catalogRequestHeaders = configuredHeaders("carappstore-prod"),
                    downloadBaseUrl = configuredDownloadBaseUrl(
                        BuildConfig.CARAPPSTORE_DOWNLOAD_PROD_BASE_URL,
                        "https://cdn.example.com/carapps",
                    ),
                )
            }
        }

        private fun configuredCatalogUrl(configured: String, fallback: String?): String? {
            return configured.trim().ifBlank { fallback }
        }

        private fun configuredDownloadBaseUrl(configured: String, fallback: String): String {
            return configured.trim().trimEnd('/').ifBlank { fallback }
        }

        private fun configuredHeaders(channel: String): Map<String, String> {
            val headers = linkedMapOf(
                "X-Client-Channel" to channel,
                "X-Client-Platform" to "android-car",
            )
            val authHeader = BuildConfig.CARAPPSTORE_CATALOG_AUTH_HEADER.trim()
            val authValue = BuildConfig.CARAPPSTORE_CATALOG_AUTH_VALUE.trim()
            if (authHeader.isNotBlank() && authValue.isNotBlank()) {
                headers[authHeader] = authValue
            }
            return headers
        }
    }
}
