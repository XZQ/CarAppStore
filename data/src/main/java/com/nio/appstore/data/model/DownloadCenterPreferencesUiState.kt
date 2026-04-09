package com.nio.appstore.data.model

data class DownloadCenterPreferencesUiState(
    val autoResumeEnabled: Boolean = false,
    val autoRetryEnabled: Boolean = true,
    val maxAutoRetryCount: Int = 2,
    val wifiConnected: Boolean = true,
    val parkingMode: Boolean = true,
    val lowStorageMode: Boolean = false,
) {
    val autoResumeText: String
        get() = "启动自动续传：" + if (autoResumeEnabled) "开" else "关"

    val autoRetryText: String
        get() = "失败自动重试：" + if (autoRetryEnabled) "开" else "关"

    val wifiText: String
        get() = "网络：" + if (wifiConnected) "Wi‑Fi" else "蜂窝"

    val parkingText: String
        get() = "车况：" + if (parkingMode) "驻车" else "行车"

    val storageText: String
        get() = "存储：" + if (lowStorageMode) "不足" else "正常"

    val summaryText: String
        get() = buildString {
            append("自动续传")
            append(if (autoResumeEnabled) "已开启" else "已关闭")
            append("，自动重试")
            append(if (autoRetryEnabled) "已开启" else "已关闭")
            append("，最多重试 ")
            append(maxAutoRetryCount)
            append(" 次。")
        }
}
