package com.nio.appstore.data.model

data class UpgradeCenterControlsUiState(
    /** 当前可直接开始的升级任务数。 */
    val runnableCount: Int = 0,
    /** 当前可重试的失败升级任务数。 */
    val failedCount: Int = 0,
) {
    /** 升级中心扩展区摘要文案。 */
    val summaryText: String
        get() = ModelText.upgradeCenterSummary(runnableCount, failedCount)

    /** 升级中心主按钮文案。 */
    val primaryText: String
        get() = ModelText.upgradeCenterPrimaryText(runnableCount)

    /** 升级中心第二按钮文案。 */
    val secondaryText: String
        get() = ModelText.upgradeCenterSecondaryText(failedCount)

    /** 升级中心第三按钮文案。 */
    val tertiaryText: String
        get() = ModelText.GO_MY_APPS
}
