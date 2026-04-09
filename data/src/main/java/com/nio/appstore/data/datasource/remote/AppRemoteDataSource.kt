package com.nio.appstore.data.datasource.remote

import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.UpgradeInfo

class AppRemoteDataSource(
    private val sourceCatalog: DownloadSourceCatalog,
) {

    private val apps = listOf(
        AppInfo("gaode_map", "com.demo.gaode", "高德地图车机版", "导航与出行服务", "1.0.0"),
        AppInfo("qq_music", "com.demo.qqmusic", "QQ音乐车机版", "在线音乐与电台", "2.3.0"),
        AppInfo("ximalaya", "com.demo.ximalaya", "喜马拉雅车机版", "有声内容与播客", "3.1.2"),
    )

    fun getHomeApps(): List<AppInfo> = apps

    fun getAppDetail(appId: String): AppDetail {
        val app = apps.firstOrNull { it.appId == appId } ?: apps.first()
        val source = sourceCatalog.get(app.appId)
        return AppDetail(
            appId = app.appId,
            packageName = app.packageName,
            name = app.name,
            description = "${app.description}，这是一个用于演示的详情页数据。",
            versionName = app.versionName,
            apkUrl = source.apkUrl,
            checksumType = source.checksumType,
            checksumValue = source.checksumValue,
            sourcePolicy = source.sourcePolicy,
        )
    }

    fun getUpgradeInfo(appId: String): UpgradeInfo {
        val detail = getAppDetail(appId)
        return when (appId) {
            "gaode_map" -> UpgradeInfo(appId, latestVersion = "1.1.0", apkUrl = detail.apkUrl, hasUpgrade = true)
            "qq_music" -> UpgradeInfo(appId, latestVersion = "2.4.0", apkUrl = detail.apkUrl, hasUpgrade = true)
            else -> UpgradeInfo(appId, latestVersion = detail.versionName, apkUrl = detail.apkUrl, hasUpgrade = false)
        }
    }
}
