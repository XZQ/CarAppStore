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
    /** 获取首页应用列表。 */
    suspend fun getHomeApps(): List<AppInfo>
    /** 获取指定应用详情。 */
    suspend fun getAppDetail(appId: String): AppDetail
    /** 获取当前已安装应用列表。 */
    suspend fun getInstalledApps(): List<InstalledApp>
    /** 标记指定应用已安装。 */
    suspend fun markInstalled(appId: String)
    /** 判断指定应用当前是否已安装。 */
    suspend fun isInstalled(appId: String): Boolean
    /** 保存下载完成的 APK 路径。 */
    suspend fun saveDownloadedApk(appId: String, apkPath: String)
    /** 读取指定应用已缓存的 APK 路径。 */
    suspend fun getDownloadedApk(appId: String): String?
    /** 清理指定应用已缓存的 APK 路径。 */
    suspend fun clearDownloadedApk(appId: String)
    /** 获取指定应用的升级信息。 */
    suspend fun getUpgradeInfo(appId: String): UpgradeInfo
    /** 暂存本次升级目标版本。 */
    suspend fun stageUpgrade(appId: String, versionName: String)
    /** 读取暂存的升级目标版本。 */
    suspend fun peekStagedUpgradeVersion(appId: String): String?
    /** 保存下载任务记录。 */
    suspend fun saveDownloadTask(record: DownloadTaskRecord)
    /** 读取指定应用的下载任务。 */
    suspend fun getDownloadTask(appId: String): DownloadTaskRecord?
    /** 读取全部下载任务。 */
    suspend fun getAllDownloadTasks(): List<DownloadTaskRecord>
    /** 删除指定应用的下载任务。 */
    suspend fun removeDownloadTask(appId: String)
    /** 清理全部已完成下载任务。 */
    suspend fun clearCompletedDownloadTasks(): Int
    /** 保存下载分片记录。 */
    suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>)
    /** 读取下载分片记录。 */
    suspend fun getDownloadSegments(appId: String): List<DownloadSegmentRecord>
    /** 获取指定应用的下载目标文件。 */
    suspend fun getOrCreateDownloadFile(appId: String): File
    /** 读取下载偏好设置。 */
    suspend fun getDownloadPreferences(): DownloadPreferences
    /** 保存下载偏好设置。 */
    suspend fun saveDownloadPreferences(preferences: DownloadPreferences)
    /** 读取策略设置。 */
    fun getPolicySettings(): PolicySettings
    /** 请求系统打开指定包名的应用。 */
    fun openApp(packageName: String): Boolean
}
