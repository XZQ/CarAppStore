package com.nio.appstore.data.repository

import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadSegmentRecord
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.data.model.InstalledApp
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.data.model.UpgradeInfo
import java.io.File

interface AppRepository {
    suspend fun getHomeApps(): List<AppInfo>
    suspend fun getAppDetail(appId: String): AppDetail
    suspend fun getInstalledApps(): List<InstalledApp>
    suspend fun markInstalled(appId: String)
    suspend fun isInstalled(appId: String): Boolean
    suspend fun saveDownloadedApk(appId: String, apkPath: String)
    suspend fun getDownloadedApk(appId: String): String?
    suspend fun clearDownloadedApk(appId: String)
    suspend fun getUpgradeInfo(appId: String): UpgradeInfo
    suspend fun stageUpgrade(appId: String, versionName: String)
    suspend fun peekStagedUpgradeVersion(appId: String): String?
    suspend fun saveDownloadTask(record: DownloadTaskRecord)
    suspend fun getDownloadTask(appId: String): DownloadTaskRecord?
    suspend fun getAllDownloadTasks(): List<DownloadTaskRecord>
    suspend fun removeDownloadTask(appId: String)
    suspend fun clearCompletedDownloadTasks(): Int
    suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>)
    suspend fun getDownloadSegments(appId: String): List<DownloadSegmentRecord>
    suspend fun getOrCreateDownloadFile(appId: String): File
    suspend fun getDownloadPreferences(): DownloadPreferences
    suspend fun saveDownloadPreferences(preferences: DownloadPreferences)
    fun getPolicySettings(): PolicySettings
    fun openApp(packageName: String): Boolean
}
