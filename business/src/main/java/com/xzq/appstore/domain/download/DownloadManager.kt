package com.xzq.appstore.domain.download

import com.xzq.appstore.data.model.DownloadPreferences

interface DownloadManager {
    /** 已下载的安装包占用；只读，不包含未完成任务的可恢复分片。 */
    suspend fun getDownloadedCacheBytes(): Long = 0L
    /** 暂停所有活动下载并等待文件 IO 收尾。 */
    suspend fun pauseAllDownloads() = Unit

    /** 系统重建执行宿主时，只恢复上次被中断的活动任务。 */
    suspend fun resumeInterruptedDownloads() = Unit

    /** 容器退出时停止传输并释放内部作用域。 */
    suspend fun close() = Unit

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
