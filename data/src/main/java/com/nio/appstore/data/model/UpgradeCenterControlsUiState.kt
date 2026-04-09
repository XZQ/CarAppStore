package com.nio.appstore.data.model

data class UpgradeCenterControlsUiState(
    val runnableCount: Int = 0,
    val failedCount: Int = 0,
) {
    val summaryText: String
        get() = buildString {
            append("当前可执行升级 ")
            append(runnableCount)
            append(" 项，失败待重试 ")
            append(failedCount)
            append(" 项。")
        }

    val primaryText: String
        get() = "批量开始升级（$runnableCount）"

    val secondaryText: String
        get() = "批量重试失败（$failedCount）"

    val tertiaryText: String
        get() = "去我的应用"
}
