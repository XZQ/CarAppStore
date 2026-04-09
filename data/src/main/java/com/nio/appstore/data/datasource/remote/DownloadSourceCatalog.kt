package com.nio.appstore.data.datasource.remote

import com.nio.appstore.data.downloadenv.DownloadEnvironment
import com.nio.appstore.core.downloader.DownloadSourcePolicy

data class DownloadSourceEntry(
    val appId: String,
    val apkUrl: String,
    val checksumType: String? = null,
    val checksumValue: String? = null,
    val sourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
)

class DownloadSourceCatalog(
    private val environment: DownloadEnvironment,
) {
    fun get(appId: String): DownloadSourceEntry {
        val table = when (environment) {
            DownloadEnvironment.DEV -> devSources()
            DownloadEnvironment.TEST -> testSources()
            DownloadEnvironment.PROD -> prodSources()
        }
        return table[appId] ?: defaultEntry(appId)
    }

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
