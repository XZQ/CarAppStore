package com.nio.appstore.data.model

data class UpgradeCenterControlsUiState(
    /** 当前可直接开始的升级任务数。 */
    val runnableCount: Int = 0,
    /** 当前可重试的失败升级任务数。 */
    val failedCount: Int = 0,
) {
    val summaryText: String
        get() = ModelText.upgradeCenterSummary(runnableCount, failedCount)

    val primaryText: String
        get() = ModelText.upgradeCenterPrimaryText(runnableCount)

    val secondaryText: String
        get() = ModelText.upgradeCenterSecondaryText(failedCount)

    val tertiaryText: String
        get() = ModelText.GO_MY_APPS
}
