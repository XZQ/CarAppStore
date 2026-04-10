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
    /** 自动续传开关对应的按钮文案。 */
    val autoResumeText: String
        get() = ModelText.autoResumeText(autoResumeEnabled)

    /** 自动重试开关对应的按钮文案。 */
    val autoRetryText: String
        get() = ModelText.autoRetryText(autoRetryEnabled)

    /** 当前网络状态对应的按钮文案。 */
    val wifiText: String
        get() = ModelText.networkText(wifiConnected)

    /** 当前驻车状态对应的按钮文案。 */
    val parkingText: String
        get() = ModelText.parkingText(parkingMode)

    /** 当前存储状态对应的按钮文案。 */
    val storageText: String
        get() = ModelText.storageText(lowStorageMode)

    /** 下载偏好扩展区的摘要文案。 */
    val summaryText: String
        get() = ModelText.downloadSummaryText(autoResumeEnabled, autoRetryEnabled, maxAutoRetryCount)
}
