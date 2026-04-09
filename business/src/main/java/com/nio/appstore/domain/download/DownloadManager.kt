package com.nio.appstore.domain.download

import com.nio.appstore.data.model.DownloadPreferences

interface DownloadManager {
    suspend fun startDownload(appId: String)
    suspend fun pauseDownload(appId: String)
    suspend fun resumeDownload(appId: String)
    suspend fun cancelDownload(appId: String)
    suspend fun removeTask(appId: String, clearFile: Boolean)
    suspend fun clearCompletedTasks(): Int
    suspend fun retryFailedTasks(): Int
    suspend fun getPreferences(): DownloadPreferences
    suspend fun updatePreferences(preferences: DownloadPreferences)
}
