package com.nio.appstore.domain.text

import com.nio.appstore.core.installer.InstallSessionStatus

/**
 * BusinessText 用于收敛业务层当前直接写在代码中的状态文案和策略提示文案。
 *
 * 这里先作为 M8 阶段的统一文案入口：
 * 1. 避免 reducer / manager / policy 中重复散落字符串；
 * 2. 不让业务层直接依赖 Android 资源；
 * 3. 为后续继续做文案策略收口和国际化迁移保留统一替换点。
 */
object BusinessText {
    const val STATUS_UPGRADING = "升级中"
    const val STATUS_UPGRADE_FAILED = "升级失败"
    const val STATUS_INSTALLING = "安装中"
    const val STATUS_WAITING_INSTALL = "等待安装"
    const val STATUS_WAITING_SYSTEM_CONFIRM = "等待系统确认"
    const val STATUS_INSTALL_FAILED = "安装失败"
    const val STATUS_UPGRADE_AVAILABLE = "可升级"
    const val STATUS_INSTALLED = "已安装"
    const val STATUS_WAITING_DOWNLOAD = "等待下载"
    const val STATUS_DOWNLOAD_COMPLETED = "下载完成"
    const val STATUS_DOWNLOAD_FAILED = "下载失败"
    const val STATUS_CANCELED = "已取消"
    const val STATUS_NOT_INSTALLED = "未安装"

    const val POLICY_NOT_WIFI = "当前非 Wi‑Fi 网络"
    const val POLICY_LOW_STORAGE = "当前存储空间不足"
    const val POLICY_DEVICE_STORAGE_LOW = "设备可用存储不足"
    const val POLICY_NOT_PARKING = "当前非驻车状态"

    const val DOWNLOAD_RECOVERED_SEGMENTS = "检测到临时分片，可继续下载"
    const val DOWNLOAD_APK_MISSING = "安装包已丢失，请重新下载"
    const val DOWNLOAD_FILE_INCOMPLETE = "文件未完整下载，可继续下载"
    const val DOWNLOAD_RECOVERED_PROGRESS = "已恢复临时下载进度，可继续下载"
    const val DOWNLOAD_FILE_MISSING = "下载文件已丢失，请重新下载"
    const val DOWNLOAD_INTERRUPTED_RESUMABLE = "上次下载中断，可继续下载"

    const val DESCRIPTION_INSTALLED_APP = "已安装应用"
    const val DESCRIPTION_APP_TASK = "应用任务"
    const val DESCRIPTION_DOWNLOAD_INSTALL_TASK = "下载/安装任务"
    const val ACTION_CLEAR_APK = "清理安装包"
    const val ACTION_DELETE_TASK = "删除任务"
    const val ACTION_CANCEL_TASK = "取消任务"
    const val PATH_APK_NOT_READY = "未生成安装包"
    const val POLICY_DOWNLOAD_CELLULAR = "当前为蜂窝网络，下载会受限"
    const val POLICY_INSTALL_DRIVING = "当前为行车状态，安装会受限"
    const val POLICY_STORAGE_LIMITED = "当前存储不足，下载和安装会受限"
    const val POLICY_ALL_CLEAR = "当前策略正常：可在 Wi‑Fi + 驻车 + 存储正常条件下执行任务"
    const val UPGRADE_DOWNLOAD_FAILED = "升级包下载失败"
    const val UPGRADE_INSTALL_FAILED = "升级安装失败"

    private const val DOWNLOADING_FORMAT = "下载中 %d%%"
    private const val PAUSED_FORMAT = "已暂停 %d%%"
    private const val DOWNLOAD_RESTRICTED_FORMAT = "下载受限：%s"
    private const val INSTALL_RESTRICTED_FORMAT = "安装受限：%s"
    private const val UPGRADE_RESTRICTED_FORMAT = "升级受限：%s"
    private const val INSTALL_FAILED_FORMAT = "安装失败：%s"
    private const val RETRY_DOWNLOAD_FORMAT = "%s，请重新下载"
    private const val SESSION_PHASE_FORMAT = "阶段：%s"
    private const val SESSION_PROGRESS_FORMAT = "会话进度 %d%%"
    private const val SESSION_FAILED_FORMAT = "安装会话失败：%s"
    private const val UPGRADE_TARGET_FORMAT = "当前 %s -> 目标 %s"
    private const val UPDATED_AT_FORMAT = "更新于 %s"
    private const val RETRY_COUNT_FORMAT = " · 重试%d次"

    fun downloading(progress: Int): String = DOWNLOADING_FORMAT.format(progress)

    fun paused(progress: Int): String = PAUSED_FORMAT.format(progress)

    fun downloadRestricted(reason: String): String = DOWNLOAD_RESTRICTED_FORMAT.format(reason)

    fun installRestricted(reason: String): String = INSTALL_RESTRICTED_FORMAT.format(reason)

    fun upgradeRestricted(reason: String): String = UPGRADE_RESTRICTED_FORMAT.format(reason)

    fun installFailed(message: String): String = INSTALL_FAILED_FORMAT.format(message)

    fun retryDownload(message: String): String = RETRY_DOWNLOAD_FORMAT.format(message)

    fun sessionPhase(status: String): String = SESSION_PHASE_FORMAT.format(sessionPhaseLabel(status))

    fun sessionProgress(progress: Int): String = SESSION_PROGRESS_FORMAT.format(progress)

    fun sessionFailed(status: String): String = SESSION_FAILED_FORMAT.format(sessionPhaseLabel(status))

    fun upgradeTarget(currentVersion: String, targetVersion: String): String = UPGRADE_TARGET_FORMAT.format(currentVersion, targetVersion)

    fun updatedAt(timeText: String): String = UPDATED_AT_FORMAT.format(timeText)

    fun retryPart(retryCount: Int): String = RETRY_COUNT_FORMAT.format(retryCount)

    private fun sessionPhaseLabel(status: String): String {
        return when (status) {
            InstallSessionStatus.CREATED -> "已创建"
            InstallSessionStatus.WRITTEN -> "已写入"
            InstallSessionStatus.COMMITTED -> "已提交"
            InstallSessionStatus.PENDING_USER_ACTION -> "等待系统确认"
            InstallSessionStatus.CALLBACK_SUCCESS -> "已完成"
            InstallSessionStatus.RECOVERED_INTERRUPTED -> "已中断，可重试"
            InstallSessionStatus.FAILED_CREATE -> "创建失败"
            InstallSessionStatus.FAILED_WRITE -> "写入失败"
            InstallSessionStatus.FAILED_COMMIT -> "提交失败"
            else -> status
        }
    }
}
