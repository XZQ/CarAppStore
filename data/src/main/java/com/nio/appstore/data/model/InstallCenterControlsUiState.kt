package com.nio.appstore.data.model

data class InstallCenterControlsUiState(
    /** 当前可直接开始的安装任务数。 */
    val runnableCount: Int = 0,
    /** 当前可清理的失败项数量。 */
    val failedCount: Int = 0,
    /** 当前可重试的安装会话数量。 */
    val retryableSessionCount: Int = 0,
    /** 中断恢复得到的安装会话数量。 */
    val recoveredSessionCount: Int = 0,
) {
    /** 安装中心扩展区摘要文案。 */
    val summaryText: String
        get() = ModelText.installCenterSummary(runnableCount, failedCount, retryableSessionCount, recoveredSessionCount)

    /** 安装中心主按钮文案。 */
    val primaryText: String
        get() = ModelText.installCenterPrimaryText(runnableCount)

    /** 安装中心第二按钮文案。 */
    val secondaryText: String
        get() = ModelText.installCenterSecondaryText(failedCount)

    /** 安装中心第三按钮文案。 */
    val tertiaryText: String
        get() = ModelText.installCenterTertiaryText(retryableSessionCount)
}
