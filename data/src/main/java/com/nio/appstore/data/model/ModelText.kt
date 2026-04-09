package com.nio.appstore.data.model

object ModelText {
    const val STATUS_NOT_INSTALLED = "未安装"

    const val FILTER_ALL_TASKS = "全部任务"
    const val FILTER_PENDING = "待处理"
    const val FILTER_ACTIVE = "执行中"
    const val FILTER_FAILED = "失败项"
    const val FILTER_COMPLETED = "已完成"

    const val SESSION_FILTER_ALL = "Session-全部"
    const val SESSION_FILTER_ACTIVE = "Session-进行中"
    const val SESSION_FILTER_FAILED = "Session-失败"
    const val SESSION_FILTER_RECOVERED = "Session-中断恢复"
    const val SESSION_FILTER_COMPLETED = "Session-已完成"

    const val SWITCH_ON = "开"
    const val SWITCH_OFF = "关"
    const val SWITCH_ENABLED = "已开启"
    const val SWITCH_DISABLED = "已关闭"
    const val NETWORK_WIFI = "Wi‑Fi"
    const val NETWORK_CELLULAR = "蜂窝"
    const val VEHICLE_PARKED = "驻车"
    const val VEHICLE_DRIVING = "行车"
    const val STORAGE_LOW = "不足"
    const val STORAGE_NORMAL = "正常"
    const val GO_MY_APPS = "去我的应用"

    private const val AUTO_RESUME_FORMAT = "启动自动续传：%s"
    private const val AUTO_RETRY_FORMAT = "失败自动重试：%s"
    private const val NETWORK_FORMAT = "网络：%s"
    private const val PARKING_FORMAT = "车况：%s"
    private const val STORAGE_FORMAT = "存储：%s"
    private const val DOWNLOAD_SUMMARY_FORMAT = "自动续传%s，自动重试%s，最多重试 %d 次。"
    private const val INSTALL_SUMMARY_FORMAT = "当前可直接安装 %d 项，失败待整理 %d 项，可重试会话 %d 项，中断恢复 %d 项。"
    private const val INSTALL_PRIMARY_FORMAT = "批量开始安装（%d）"
    private const val INSTALL_SECONDARY_FORMAT = "清理失败 Session（%d）"
    private const val INSTALL_TERTIARY_FORMAT = "重试 Session（%d）"
    private const val UPGRADE_SUMMARY_FORMAT = "当前可执行升级 %d 项，失败待重试 %d 项。"
    private const val UPGRADE_PRIMARY_FORMAT = "批量开始升级（%d）"
    private const val UPGRADE_SECONDARY_FORMAT = "批量重试失败（%d）"
    private const val DEMO_DETAIL_DESCRIPTION_FORMAT = "%s，这是一个用于演示的详情页数据。"

    fun autoResumeText(enabled: Boolean): String = AUTO_RESUME_FORMAT.format(if (enabled) SWITCH_ON else SWITCH_OFF)

    fun autoRetryText(enabled: Boolean): String = AUTO_RETRY_FORMAT.format(if (enabled) SWITCH_ON else SWITCH_OFF)

    fun networkText(wifiConnected: Boolean): String = NETWORK_FORMAT.format(if (wifiConnected) NETWORK_WIFI else NETWORK_CELLULAR)

    fun parkingText(parkingMode: Boolean): String = PARKING_FORMAT.format(if (parkingMode) VEHICLE_PARKED else VEHICLE_DRIVING)

    fun storageText(lowStorageMode: Boolean): String = STORAGE_FORMAT.format(if (lowStorageMode) STORAGE_LOW else STORAGE_NORMAL)

    fun downloadSummaryText(autoResumeEnabled: Boolean, autoRetryEnabled: Boolean, maxAutoRetryCount: Int): String =
        DOWNLOAD_SUMMARY_FORMAT.format(
            if (autoResumeEnabled) SWITCH_ENABLED else SWITCH_DISABLED,
            if (autoRetryEnabled) SWITCH_ENABLED else SWITCH_DISABLED,
            maxAutoRetryCount,
        )

    fun installCenterSummary(runnableCount: Int, failedCount: Int, retryableSessionCount: Int, recoveredSessionCount: Int): String =
        INSTALL_SUMMARY_FORMAT.format(runnableCount, failedCount, retryableSessionCount, recoveredSessionCount)

    fun installCenterPrimaryText(runnableCount: Int): String = INSTALL_PRIMARY_FORMAT.format(runnableCount)

    fun installCenterSecondaryText(failedCount: Int): String = INSTALL_SECONDARY_FORMAT.format(failedCount)

    fun installCenterTertiaryText(retryableSessionCount: Int): String = INSTALL_TERTIARY_FORMAT.format(retryableSessionCount)

    fun upgradeCenterSummary(runnableCount: Int, failedCount: Int): String = UPGRADE_SUMMARY_FORMAT.format(runnableCount, failedCount)

    fun upgradeCenterPrimaryText(runnableCount: Int): String = UPGRADE_PRIMARY_FORMAT.format(runnableCount)

    fun upgradeCenterSecondaryText(failedCount: Int): String = UPGRADE_SECONDARY_FORMAT.format(failedCount)

    fun demoDetailDescription(description: String): String = DEMO_DETAIL_DESCRIPTION_FORMAT.format(description)
}
