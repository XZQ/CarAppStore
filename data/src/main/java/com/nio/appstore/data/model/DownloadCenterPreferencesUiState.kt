package com.nio.appstore.data.model

data class DownloadCenterPreferencesUiState(
    /** 启动应用时是否自动恢复中断下载。 */
    val autoResumeEnabled: Boolean = false,
    /** 下载失败后是否自动重试。 */
    val autoRetryEnabled: Boolean = true,
    /** 自动重试的最大次数。 */
    val maxAutoRetryCount: Int = 2,
    /** 当前网络条件是否为无线网络。 */
    val wifiConnected: Boolean = true,
    /** 当前车辆状态是否为驻车。 */
    val parkingMode: Boolean = true,
    /** 当前设备是否处于低存储状态。 */
    val lowStorageMode: Boolean = false,
) {
    val autoResumeText: String
        get() = ModelText.autoResumeText(autoResumeEnabled)

    val autoRetryText: String
        get() = ModelText.autoRetryText(autoRetryEnabled)

    val wifiText: String
        get() = ModelText.networkText(wifiConnected)

    val parkingText: String
        get() = ModelText.parkingText(parkingMode)

    val storageText: String
        get() = ModelText.storageText(lowStorageMode)

    val summaryText: String
        get() = ModelText.downloadSummaryText(autoResumeEnabled, autoRetryEnabled, maxAutoRetryCount)
}
