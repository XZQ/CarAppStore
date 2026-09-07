package com.xzq.appstore.data.repository

import com.xzq.appstore.core.logger.AppLogger
import com.xzq.appstore.data.datasource.local.AppLocalDataSource
import com.xzq.appstore.data.datasource.remote.AppRemoteDataSource
import com.xzq.appstore.data.datasource.system.AppSystemDataSource
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.AppInfo
import com.xzq.appstore.data.model.DownloadPreferences
import com.xzq.appstore.data.model.DownloadSegmentRecord
import com.xzq.appstore.data.model.DownloadTaskRecord
import com.xzq.appstore.data.model.InstalledApp
import com.xzq.appstore.data.model.PolicySettings
import com.xzq.appstore.data.model.UpgradeInfo
import java.io.File

class RealAppRepository(
    /** 远端数据源，提供应用列表、详情和升级信息。 */
    private val remote: AppRemoteDataSource,
    /** 本地数据源，负责任务、设置和安装信息持久化。 */
    private val local: AppLocalDataSource,
    /** 系统数据源，负责打开应用和查询包信息等系统能力。 */
    private val system: AppSystemDataSource,
    /** 统一日志入口，记录回退时的诊断信息。 */
    private val logger: AppLogger = AppLogger(),
) : AppRepository {
    /** 获取首页应用列表，远端失败时降级到本地已安装快照，避免页面直接报错。 */
    override suspend fun getHomeApps(): List<AppInfo> = runCatching { remote.getHomeApps() }
        .onFailure { logger.d(TAG, "getHomeApps remote failed: ${it.message}, fallback to local") }
        .getOrElse { local.getInstalledApps().map { it.toAppInfo() } }

    override suspend fun getRecentlyOpenedPackages(): List<String> = local.getRecentlyOpenedPackages()

    /** 获取指定应用详情，远端失败时尝试用本地已安装快照兜底，仍失败则抛原始异常。 */
    override suspend fun getAppDetail(appId: String): AppDetail = runCatching { remote.getAppDetail(appId) }.getOrElse { throwable ->
        logger.d(TAG, "getAppDetail remote failed: ${throwable.message}, fallback to local")
        local.getInstalledApps().firstOrNull { it.appId == appId }?.toAppDetail() ?: throw throwable
    }

    /** 获取已安装应用列表，以 PackageManager 为真相，本地镜像只在目录不可用时兜底。 */
    override suspend fun getInstalledApps(): List<InstalledApp> {
        val catalog = runCatching { remote.getHomeApps() }.getOrElse { return local.getInstalledApps() }
        val catalogAppIds = catalog.associateBy { it.packageName }
        return system.queryInstalledApps(catalogAppIds.keys).map { installed ->
            val catalogApp = catalogAppIds[installed.packageName]
            installed.copy(appId = catalogApp?.appId ?: installed.appId, name = catalogApp?.name ?: installed.name)
        }
    }

    /** 以系统查询的版本写入安装镜像；目录不可达时从已有镜像解析包名。 */
    override suspend fun markInstalled(appId: String) {
        val detail = runCatching { remote.getAppDetail(appId) }
            .onFailure { logger.d(TAG, "markInstalled remote failed: ${it.message}, fallback to local") }
            .getOrNull()
        val packageName = detail?.packageName ?: local.getInstalledApps().firstOrNull { it.appId == appId }?.packageName ?: return
        val installed = system.queryInstalledApps(setOf(packageName)).singleOrNull() ?: return
        local.saveInstalledApp(installed.copy(appId = appId, name = detail?.name ?: installed.name))
        local.consumeStagedUpgradeVersion(appId)
    }

    /** 判断指定应用是否已安装，优先使用系统包管理器而不是本地镜像。 */
    override suspend fun isInstalled(appId: String): Boolean {
        val detail = runCatching { remote.getAppDetail(appId) }.getOrNull()
        return if (detail != null) system.isPackageInstalled(detail.packageName) else local.isInstalled(appId)
    }

    /** 保存已下载 APK 路径。 */
    override suspend fun saveDownloadedApk(appId: String, apkPath: String) {
        local.saveDownloadedApk(appId, apkPath)
    }

    /** 获取已下载 APK 路径。 */
    override suspend fun getDownloadedApk(appId: String): String? = local.getDownloadedApk(appId)

    /** 清理已下载 APK 路径和本地文件。 */
    override suspend fun clearDownloadedApk(appId: String) {
        local.clearDownloadedApk(appId)
    }

    /** 获取升级信息。 */
    override suspend fun getUpgradeInfo(appId: String): UpgradeInfo = remote.getUpgradeInfo(appId)

    /** 保存 staged upgrade 目标版本。 */
    override suspend fun stageUpgrade(appId: String, versionName: String) {
        local.stageUpgradeVersion(appId, versionName)
    }

    /** 读取 staged upgrade 目标版本。 */
    override suspend fun peekStagedUpgradeVersion(appId: String): String? = local.peekStagedUpgradeVersion(appId)

    /** 保存下载任务记录。 */
    override suspend fun saveDownloadTask(record: DownloadTaskRecord) {
        local.saveDownloadTask(record)
    }

    /** 获取指定应用的下载任务记录。 */
    override suspend fun getDownloadTask(appId: String): DownloadTaskRecord? = local.getDownloadTask(appId)

    /** 获取全部下载任务记录。 */
    override suspend fun getAllDownloadTasks(): List<DownloadTaskRecord> = local.getAllDownloadTasks()

    /** 删除指定应用的下载任务。 */
    override suspend fun removeDownloadTask(appId: String) {
        local.removeDownloadTask(appId)
    }

    /** 清理所有已完成的下载任务。 */
    override suspend fun clearCompletedDownloadTasks(): Int = local.clearCompletedDownloadTasks()

    /** 保存指定应用的下载分片记录。 */
    override suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) {
        local.saveDownloadSegments(appId, segments)
    }

    /** 获取指定应用的下载分片记录。 */
    override suspend fun getDownloadSegments(appId: String): List<DownloadSegmentRecord> = local.getDownloadSegments(appId)

    /** 获取指定应用默认的下载目标文件。 */
    override suspend fun getOrCreateDownloadFile(appId: String): File = local.getOrCreateDownloadFile(appId)

    /** 获取下载偏好配置。 */
    override suspend fun getDownloadPreferences(): DownloadPreferences = local.getDownloadPreferences()

    /** 保存下载偏好配置。 */
    override suspend fun saveDownloadPreferences(preferences: DownloadPreferences) {
        local.saveDownloadPreferences(preferences)
    }

    /** 获取策略设置。 */
    override fun getPolicySettings(): PolicySettings = local.getPolicySettings()

    /** 尝试打开指定包名的应用。 */
    override fun openApp(packageName: String): Boolean = system.openApp(packageName)

    override fun recordRecentlyOpenedPackage(packageName: String) {
        local.recordRecentlyOpenedPackage(packageName)
    }

    /** InstalledApp → AppInfo 的本地兜底映射，仅用于远端不可达场景。 */
    private fun InstalledApp.toAppInfo(): AppInfo = AppInfo(appId = appId, packageName = packageName, name = name, description = "", versionName = versionName)

    /** InstalledApp → AppDetail 的本地兜底映射，仅含最小可用字段。 */
    private fun InstalledApp.toAppDetail(): AppDetail = AppDetail(
        appId = appId,
        packageName = packageName,
        name = name,
        description = "",
        versionName = versionName,
        versionCode = versionCode,
        apkUrl = "",
    )

    private companion object {
        private const val TAG = "RealAppRepository"
    }
}
