package com.nio.appstore.data.datasource.remote

import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.ModelText
import com.nio.appstore.data.model.UpgradeInfo

class AppRemoteDataSource(
    /** 当前环境下的下载源目录。 */
    private val sourceCatalog: DownloadSourceCatalog,
) {

    /** 演示用的远端应用列表。 */
    private val apps = listOf(
        AppInfo("gaode_map", "com.demo.gaode", "高德地图车机版", "导航与出行服务", "1.0.0"),
        AppInfo("qq_music", "com.demo.qqmusic", "QQ音乐车机版", "在线音乐与电台", "2.3.0"),
        AppInfo("ximalaya", "com.demo.ximalaya", "喜马拉雅车机版", "有声内容与播客", "3.1.2"),
    )

    /** 返回首页应用列表。 */
    fun getHomeApps(): List<AppInfo> = apps

    /** 根据 appId 返回应用详情，并补全当前环境下的下载源信息。 */
    fun getAppDetail(appId: String): AppDetail {
        val app = apps.firstOrNull { it.appId == appId } ?: apps.first()
        // 详情数据中的下载地址、校验值和下载策略统一来自下载源目录。
        val source = sourceCatalog.get(app.appId)
        return AppDetail(
            appId = app.appId,
            packageName = app.packageName,
            name = app.name,
            description = ModelText.demoDetailDescription(app.description),
            versionName = app.versionName,
            apkUrl = source.apkUrl,
            checksumType = source.checksumType,
            checksumValue = source.checksumValue,
            sourcePolicy = source.sourcePolicy,
        )
    }

    /** 返回指定应用的升级信息。 */
    fun getUpgradeInfo(appId: String): UpgradeInfo {
        val detail = getAppDetail(appId)
        return when (appId) {
            "gaode_map" -> UpgradeInfo(appId, latestVersion = "1.1.0", apkUrl = detail.apkUrl, hasUpgrade = true)
            "qq_music" -> UpgradeInfo(appId, latestVersion = "2.4.0", apkUrl = detail.apkUrl, hasUpgrade = true)
            else -> UpgradeInfo(appId, latestVersion = detail.versionName, apkUrl = detail.apkUrl, hasUpgrade = false)
        }
    }
}
