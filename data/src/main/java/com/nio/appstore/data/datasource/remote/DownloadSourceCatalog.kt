package com.nio.appstore.data.datasource.remote

import com.nio.appstore.data.downloadenv.DownloadEnvironmentConfig
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
    /** 当前生效的环境配置，包含下载基地址和环境标识。 */
    private val config: DownloadEnvironmentConfig,
) {
    /** 读取指定应用在当前环境下的下载源配置。 */
    fun get(appId: String): DownloadSourceEntry {
        val table = envSources()
        return table[appId] ?: defaultEntry(appId)
    }

    /** 当前环境下已知应用的下载源目录，策略和 URL 全部从配置读取。 */
    private fun envSources(): Map<String, DownloadSourceEntry> {
        return mapOf(
            "gaode_map" to DownloadSourceEntry(
                appId = "gaode_map",
                apkUrl = "${config.downloadBaseUrl}/gaode_map.apk",
                checksumType = "SHA-256",
                sourcePolicy = config.defaultSourcePolicy,
            ),
            "qq_music" to DownloadSourceEntry(
                appId = "qq_music",
                apkUrl = "${config.downloadBaseUrl}/qq_music.apk",
                checksumType = "SHA-256",
                sourcePolicy = if (config.allowMockSource) DownloadSourcePolicy.MOCK else config.defaultSourcePolicy,
            ),
            "ximalaya" to DownloadSourceEntry(
                appId = "ximalaya",
                apkUrl = "${config.downloadBaseUrl}/ximalaya.apk",
                checksumType = "SHA-256",
                sourcePolicy = config.defaultSourcePolicy,
            ),
        )
    }

    /** 未显式配置应用时的默认下载源配置，策略从配置读取。 */
    private fun defaultEntry(appId: String): DownloadSourceEntry {
        return DownloadSourceEntry(
            appId = appId,
            apkUrl = "${config.downloadBaseUrl}/$appId.apk",
            checksumType = "SHA-256",
            sourcePolicy = config.defaultSourcePolicy,
        )
    }
}
