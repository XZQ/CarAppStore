package com.nio.appstore.data.datasource.remote

import com.nio.appstore.data.downloadenv.DownloadEnvironment
import com.nio.appstore.core.downloader.DownloadSourcePolicy

data class DownloadSourceEntry(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 当前应用使用的下载地址。 */
    val apkUrl: String,
    /** 文件校验使用的可选算法。 */
    val checksumType: String? = null,
    /** 文件校验使用的可选校验值。 */
    val checksumValue: String? = null,
    /** 当前环境下该应用优先采用的下载源策略。 */
    val sourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
)

class DownloadSourceCatalog(
    /** 当前生效的下载环境。 */
    private val environment: DownloadEnvironment,
) {
    /** 读取指定应用在当前环境下的下载源配置。 */
    fun get(appId: String): DownloadSourceEntry {
        val table = when (environment) {
            DownloadEnvironment.DEV -> devSources()
            DownloadEnvironment.TEST -> testSources()
            DownloadEnvironment.PROD -> prodSources()
        }
        return table[appId] ?: defaultEntry(appId)
    }

    /** 开发环境下载源目录。 */
    private fun devSources(): Map<String, DownloadSourceEntry> = mapOf(
        "gaode_map" to DownloadSourceEntry(
            appId = "gaode_map",
            apkUrl = "https://example.com/gaode_map.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
        ),
        "qq_music" to DownloadSourceEntry(
            appId = "qq_music",
            apkUrl = "https://example.com/qq_music.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.MOCK,
        ),
        "ximalaya" to DownloadSourceEntry(
            appId = "ximalaya",
            apkUrl = "https://download.example.org/ximalaya.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        ),
    )

    /** 测试环境下载源目录。 */
    private fun testSources(): Map<String, DownloadSourceEntry> = mapOf(
        "gaode_map" to DownloadSourceEntry(
            appId = "gaode_map",
            apkUrl = "https://test-download.example.org/gaode_map.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        ),
        "qq_music" to DownloadSourceEntry(
            appId = "qq_music",
            apkUrl = "https://test-download.example.org/qq_music.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        ),
        "ximalaya" to DownloadSourceEntry(
            appId = "ximalaya",
            apkUrl = "https://test-download.example.org/ximalaya.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        ),
    )

    /** 生产环境下载源目录。 */
    private fun prodSources(): Map<String, DownloadSourceEntry> = mapOf(
        "gaode_map" to DownloadSourceEntry(
            appId = "gaode_map",
            apkUrl = "https://cdn.example.com/carapps/gaode_map.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        ),
        "qq_music" to DownloadSourceEntry(
            appId = "qq_music",
            apkUrl = "https://cdn.example.com/carapps/qq_music.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        ),
        "ximalaya" to DownloadSourceEntry(
            appId = "ximalaya",
            apkUrl = "https://cdn.example.com/carapps/ximalaya.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        ),
    )

    /** 未显式配置应用时的默认下载源配置。 */
    private fun defaultEntry(appId: String): DownloadSourceEntry {
        return DownloadSourceEntry(
            appId = appId,
            apkUrl = "https://example.com/$appId.apk",
            checksumType = "SHA-256",
            checksumValue = null,
            sourcePolicy = if (environment == DownloadEnvironment.DEV) {
                DownloadSourcePolicy.FALLBACK_SIMULATED
            } else {
                DownloadSourcePolicy.DIRECT_HTTP
            },
        )
    }
}
