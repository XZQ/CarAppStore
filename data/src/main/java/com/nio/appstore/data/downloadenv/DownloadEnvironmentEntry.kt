package com.nio.appstore.data.downloadenv

class DownloadEnvironmentEntry(
    /** 下载环境提供者，负责读取当前环境选择。 */
    private val provider: DownloadEnvironmentProvider,
) {
    /** 读取当前环境并转换为完整环境配置。 */
    fun currentConfig(): DownloadEnvironmentConfig {
        return DownloadEnvironmentConfig.forEnvironment(provider.getCurrentEnvironment())
    }
}
