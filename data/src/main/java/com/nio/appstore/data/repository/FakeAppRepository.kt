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
    private val remote: AppRemoteDataSource,
    private val local: AppLocalDataSource,
    private val system: AppSystemDataSource,
) : AppRepository {

    override suspend fun getHomeApps(): List<AppInfo> {
        delay(200)
        return remote.getHomeApps()
    }

    override suspend fun getAppDetail(appId: String): AppDetail {
        delay(120)
        return remote.getAppDetail(appId)
    }

    override suspend fun getInstalledApps(): List<InstalledApp> {
        delay(80)
        return local.getInstalledApps()
    }

    override suspend fun markInstalled(appId: String) {
        val detail = remote.getAppDetail(appId)
        val stagedVersion = local.consumeStagedUpgradeVersion(appId)
        local.saveInstalledApp(
            InstalledApp(
                appId = detail.appId,
                packageName = detail.packageName,
                name = detail.name,
                versionName = stagedVersion ?: detail.versionName,
            )
        )
    }

    override suspend fun isInstalled(appId: String): Boolean {
        delay(30)
        return local.isInstalled(appId)
    }

    override suspend fun saveDownloadedApk(appId: String, apkPath: String) {
        delay(20)
        local.saveDownloadedApk(appId, apkPath)
    }

    override suspend fun getDownloadedApk(appId: String): String? {
        delay(20)
        return local.getDownloadedApk(appId)
    }

    override suspend fun clearDownloadedApk(appId: String) {
        delay(20)
        local.clearDownloadedApk(appId)
    }

    override suspend fun getUpgradeInfo(appId: String): UpgradeInfo {
        delay(100)
        return remote.getUpgradeInfo(appId)
    }

    override suspend fun stageUpgrade(appId: String, versionName: String) {
        delay(20)
        local.stageUpgradeVersion(appId, versionName)
    }

    override suspend fun peekStagedUpgradeVersion(appId: String): String? {
        delay(20)
        return local.peekStagedUpgradeVersion(appId)
    }

    override suspend fun saveDownloadTask(record: DownloadTaskRecord) {
        delay(10)
        local.saveDownloadTask(record)
    }

    override suspend fun getDownloadTask(appId: String): DownloadTaskRecord? {
        delay(10)
        return local.getDownloadTask(appId)
    }

    override suspend fun getAllDownloadTasks(): List<DownloadTaskRecord> {
        delay(20)
        return local.getAllDownloadTasks()
    }

    override suspend fun removeDownloadTask(appId: String) {
        delay(10)
        local.removeDownloadTask(appId)
    }

    override suspend fun clearCompletedDownloadTasks(): Int {
        delay(20)
        return local.clearCompletedDownloadTasks()
    }


    override suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) {
        delay(5)
        local.saveDownloadSegments(appId, segments)
    }

    override suspend fun getDownloadSegments(appId: String): List<DownloadSegmentRecord> {
        delay(5)
        return local.getDownloadSegments(appId)
    }

    override suspend fun getOrCreateDownloadFile(appId: String): File {
        delay(10)
        return local.getOrCreateDownloadFile(appId)
    }

    override suspend fun getDownloadPreferences(): DownloadPreferences {
        delay(10)
        return local.getDownloadPreferences()
    }

    override suspend fun saveDownloadPreferences(preferences: DownloadPreferences) {
        delay(10)
        local.saveDownloadPreferences(preferences)
    }

    override fun getPolicySettings(): PolicySettings = local.getPolicySettings()

    override fun openApp(packageName: String): Boolean = system.openApp(packageName)
}
