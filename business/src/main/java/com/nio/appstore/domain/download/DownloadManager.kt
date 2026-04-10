package com.nio.appstore.domain.download

import com.nio.appstore.data.model.DownloadPreferences

interface DownloadManager {
    /** 启动指定应用的下载流程。 */
    suspend fun startDownload(appId: String)

    /** 将指定应用的下载任务切换为暂停状态。 */
    suspend fun pauseDownload(appId: String)

    /** 恢复指定应用的下载任务。 */
    suspend fun resumeDownload(appId: String)

    /** 取消指定应用的下载任务，并清理运行态。 */
    suspend fun cancelDownload(appId: String)

    /** 删除指定应用的下载任务，并按需删除本地文件。 */
    suspend fun removeTask(appId: String, clearFile: Boolean)

    /** 清理所有已完成的下载任务。 */
    suspend fun clearCompletedTasks(): Int

    /** 重试所有失败的下载任务。 */
    suspend fun retryFailedTasks(): Int

    /** 读取下载偏好配置。 */
    suspend fun getPreferences(): DownloadPreferences

    /** 持久化新的下载偏好配置。 */
    suspend fun updatePreferences(preferences: DownloadPreferences)
}
