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

    /** 本地模拟环境：目录与 APK 均来自本机静态服务器，用于离线/无后端联调。 */
    LOCAL_SIM,
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
    /** 当前环境下 APK/CDN 下载附加请求头；目录下发签名 URL 时保持为空。 */
    val downloadRequestHeaders: Map<String, String> = emptyMap(),
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
                    catalogRequestHeaders = configuredCatalogHeaders("carappstore-dev"),
                    downloadRequestHeaders = configuredDownloadHeaders(),
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
                    catalogRequestHeaders = configuredCatalogHeaders("carappstore-test"),
                    downloadRequestHeaders = configuredDownloadHeaders(),
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
                    catalogEndpointUrl = configuredCatalogUrl(BuildConfig.CARAPPSTORE_CATALOG_PROD_URL, null),
                    catalogRequestHeaders = configuredCatalogHeaders("carappstore-prod"),
                    downloadRequestHeaders = configuredDownloadHeaders(),
                    downloadBaseUrl = configuredDownloadBaseUrl(BuildConfig.CARAPPSTORE_DOWNLOAD_PROD_BASE_URL, ""),
                )

                DownloadEnvironment.LOCAL_SIM -> DownloadEnvironmentConfig(
                    environment = environment,
                    defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
                    allowMockSource = true,
                    allowDirectHttp = true,
                    catalogEndpointUrl = "http://10.0.2.2:8080/catalog.json",
                    catalogRequestHeaders = configuredCatalogHeaders("carappstore-local"),
                    downloadRequestHeaders = configuredDownloadHeaders(),
                    downloadBaseUrl = "http://10.0.2.2:8080",
                )
            }
        }

        private fun configuredCatalogUrl(configured: String, fallback: String?): String? {
            return configured.trim().ifBlank { fallback }
        }

        private fun configuredDownloadBaseUrl(configured: String, fallback: String): String {
            return configured.trim().trimEnd('/').ifBlank { fallback }
        }

        private fun configuredCatalogHeaders(channel: String): Map<String, String> {
            val headers = linkedMapOf("X-Client-Channel" to channel, "X-Client-Platform" to "android")
            headers += configuredAuthenticationHeaders(
                BuildConfig.CARAPPSTORE_CATALOG_AUTH_HEADER,
                BuildConfig.CARAPPSTORE_CATALOG_AUTH_VALUE,
            )
            return headers
        }

        private fun configuredDownloadHeaders(): Map<String, String> =
            configuredAuthenticationHeaders(
                BuildConfig.CARAPPSTORE_DOWNLOAD_AUTH_HEADER,
                BuildConfig.CARAPPSTORE_DOWNLOAD_AUTH_VALUE,
            )

        private fun configuredAuthenticationHeaders(rawHeader: String, rawValue: String): Map<String, String> {
            val authHeader = rawHeader.trim()
            val authValue = rawValue.trim()
            if (authHeader.isNotBlank() && authValue.isNotBlank()) {
                return mapOf(authHeader to authValue)
            }
            return emptyMap()
        }
    }
}
