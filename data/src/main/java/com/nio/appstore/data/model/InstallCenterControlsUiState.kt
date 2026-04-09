package com.nio.appstore.data.model

data class InstallCenterControlsUiState(
    val runnableCount: Int = 0,
    val failedCount: Int = 0,
    val retryableSessionCount: Int = 0,
    val recoveredSessionCount: Int = 0,
) {
    val summaryText: String
        get() = buildString {
            append("当前可直接安装 ")
            append(runnableCount)
            append(" 项，失败待整理 ")
            append(failedCount)
            append(" 项，")
            append("可重试会话 ")
            append(retryableSessionCount)
            append(" 项，中断恢复 ")
            append(recoveredSessionCount)
            append(" 项。")
        }

    val primaryText: String
        get() = "批量开始安装（$runnableCount）"

    val secondaryText: String
        get() = "清理失败 Session（$failedCount）"

    val tertiaryText: String
        get() = "重试 Session（$retryableSessionCount）"
}
