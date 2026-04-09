package com.nio.appstore.data.downloadenv

class DownloadEnvironmentEntry(
    private val provider: DownloadEnvironmentProvider,
) {
    fun currentConfig(): DownloadEnvironmentConfig {
        return DownloadEnvironmentConfig.forEnvironment(provider.getCurrentEnvironment())
    }
}
