package com.nio.appstore.data.repository

import com.nio.appstore.data.datasource.local.AppLocalDataSource
import com.nio.appstore.data.datasource.remote.AppRemoteDataSource
import com.nio.appstore.data.datasource.system.AppSystemDataSource
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadSegmentRecord
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.data.model.InstalledApp
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.data.model.UpgradeInfo
import kotlinx.coroutines.delay
import java.io.File

class FakeAppRepository(
    /** 远端数据源，当前提供应用列表、详情和升级信息。 */
    private val remote: AppRemoteDataSource,
    /** 本地数据源，负责任务、设置和安装信息持久化。 */
    private val local: AppLocalDataSource,
    /** 系统数据源，负责打开应用等系统能力。 */
    private val system: AppSystemDataSource,
) : AppRepository {

    /** 获取首页应用列表。 */
    override suspend fun getHomeApps(): List<AppInfo> {
        delay(200)
        return remote.getHomeApps()
    }

    /** 获取指定应用详情。 */
    override suspend fun getAppDetail(appId: String): AppDetail {
        delay(120)
        return remote.getAppDetail(appId)
    }

    /** 获取已安装应用列表。 */
    override suspend fun getInstalledApps(): List<InstalledApp> {
        delay(80)
        return local.getInstalledApps()
    }

    /** 将指定应用标记为已安装。 */
    override suspend fun markInstalled(appId: String) {
        val detail = remote.getAppDetail(appId)
        val stagedVersion = local.consumeStagedUpgradeVersion(appId)
        // 如果当前是升级链路，则优先写入 staged 目标版本，避免回退到旧版本号。
        local.saveInstalledApp(
            InstalledApp(
                appId = detail.appId,
                packageName = detail.packageName,
                name = detail.name,
                versionName = stagedVersion ?: detail.versionName,
            )
        )
    }

    /** 判断指定应用是否已安装。 */
    override suspend fun isInstalled(appId: String): Boolean {
        delay(30)
        return local.isInstalled(appId)
    }

    /** 保存已下载 APK 路径。 */
    override suspend fun saveDownloadedApk(appId: String, apkPath: String) {
        delay(20)
        local.saveDownloadedApk(appId, apkPath)
    }

    /** 获取已下载 APK 路径。 */
    override suspend fun getDownloadedApk(appId: String): String? {
        delay(20)
        return local.getDownloadedApk(appId)
    }

    /** 清理已下载 APK 路径和本地文件。 */
    override suspend fun clearDownloadedApk(appId: String) {
        delay(20)
        local.clearDownloadedApk(appId)
    }

    /** 获取升级信息。 */
    override suspend fun getUpgradeInfo(appId: String): UpgradeInfo {
        delay(100)
        return remote.getUpgradeInfo(appId)
    }

    /** 保存 staged upgrade 目标版本。 */
    override suspend fun stageUpgrade(appId: String, versionName: String) {
        delay(20)
        local.stageUpgradeVersion(appId, versionName)
    }

    /** 读取 staged upgrade 目标版本。 */
    override suspend fun peekStagedUpgradeVersion(appId: String): String? {
        delay(20)
        return local.peekStagedUpgradeVersion(appId)
    }

    /** 保存下载任务记录。 */
    override suspend fun saveDownloadTask(record: DownloadTaskRecord) {
        delay(10)
        local.saveDownloadTask(record)
    }

    /** 获取指定应用的下载任务记录。 */
    override suspend fun getDownloadTask(appId: String): DownloadTaskRecord? {
        delay(10)
        return local.getDownloadTask(appId)
    }

    /** 获取全部下载任务记录。 */
    override suspend fun getAllDownloadTasks(): List<DownloadTaskRecord> {
        delay(20)
        return local.getAllDownloadTasks()
    }

    /** 删除指定应用的下载任务。 */
    override suspend fun removeDownloadTask(appId: String) {
        delay(10)
        local.removeDownloadTask(appId)
    }

    /** 清理所有已完成的下载任务。 */
    override suspend fun clearCompletedDownloadTasks(): Int {
        delay(20)
        return local.clearCompletedDownloadTasks()
    }

    /** 保存指定应用的下载分片记录。 */
    override suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) {
        delay(5)
        local.saveDownloadSegments(appId, segments)
    }

    /** 获取指定应用的下载分片记录。 */
    override suspend fun getDownloadSegments(appId: String): List<DownloadSegmentRecord> {
        delay(5)
        return local.getDownloadSegments(appId)
    }

    /** 获取指定应用默认的下载目标文件。 */
    override suspend fun getOrCreateDownloadFile(appId: String): File {
        delay(10)
        return local.getOrCreateDownloadFile(appId)
    }

    /** 获取下载偏好配置。 */
    override suspend fun getDownloadPreferences(): DownloadPreferences {
        delay(10)
        return local.getDownloadPreferences()
    }

    /** 保存下载偏好配置。 */
    override suspend fun saveDownloadPreferences(preferences: DownloadPreferences) {
        delay(10)
        local.saveDownloadPreferences(preferences)
    }

    /** 获取策略设置。 */
    override fun getPolicySettings(): PolicySettings = local.getPolicySettings()

    /** 尝试打开指定包名的应用。 */
    override fun openApp(packageName: String): Boolean = system.openApp(packageName)
}
