package com.nio.appstore.data.model

/**
 * DownloadPreferences 描述下载中心的持久化偏好设置。
 */
data class DownloadPreferences(
    /** 启动应用时是否自动恢复暂停或中断的下载任务。 */
    val autoResumeOnLaunch: Boolean = false,
    /** 失败下载是否自动重试。 */
    val autoRetryEnabled: Boolean = true,
    /** 自动重试的最大次数。 */
    val maxAutoRetryCount: Int = 2,
)
