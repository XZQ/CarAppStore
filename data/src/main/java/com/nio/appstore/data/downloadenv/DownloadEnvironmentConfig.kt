package com.nio.appstore.data.downloadenv

import com.nio.appstore.core.downloader.DownloadSourcePolicy

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
                )
                DownloadEnvironment.TEST -> DownloadEnvironmentConfig(
                    environment = environment,
                    defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
                    allowMockSource = true,
                    allowDirectHttp = true,
                )
                DownloadEnvironment.PROD -> DownloadEnvironmentConfig(
                    environment = environment,
                    defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
                    allowMockSource = false,
                    allowDirectHttp = true,
                )
            }
        }
    }
}
