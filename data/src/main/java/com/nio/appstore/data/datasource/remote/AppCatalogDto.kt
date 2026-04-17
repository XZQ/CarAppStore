package com.nio.appstore.data.datasource.remote

import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.UpgradeInfo

/**
 * AppCatalogResponse 描述远端商店目录接口响应。
 */
data class AppCatalogResponse(
    /** 目录中的应用集合。 */
    val apps: List<AppCatalogItemResponse>,
)

/**
 * AppCatalogItemResponse 描述目录中的单个应用。
 */
data class AppCatalogItemResponse(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 应用包名。 */
    val packageName: String,
    /** 应用名称。 */
    val name: String,
    /** 简要描述。 */
    val description: String,
    /** 当前版本号。 */
    val versionName: String,
    /** 分类。 */
    val category: String,
    /** 运营标签。 */
    val editorialTag: String,
    /** 推荐理由。 */
    val recommendedReason: String,
    /** 搜索关键词。 */
    val searchKeywords: List<String>,
    /** 开发者名称。 */
    val developerName: String,
    /** 评分文案。 */
    val ratingText: String,
    /** 包体大小文案。 */
    val sizeText: String,
    /** 最后更新时间文案。 */
    val lastUpdatedText: String,
    /** 兼容性说明。 */
    val compatibilitySummary: String,
    /** 权限说明。 */
    val permissionsSummary: String,
    /** 更新摘要。 */
    val updateSummary: String,
    /** 最新版本号。 */
    val latestVersion: String,
    /** 是否可升级。 */
    val hasUpgrade: Boolean,
    /** 升级变更摘要。 */
    val changelog: String,
)

/**
 * 把目录响应项转换成远端目录领域模型。
 */
fun AppCatalogItemResponse.toRemoteCatalogItem(): RemoteCatalogItem {
    return RemoteCatalogItem(
        appId = appId,
        appInfo = AppInfo(
            appId = appId,
            packageName = packageName,
            name = name,
            description = description,
            versionName = versionName,
            category = category,
            editorialTag = editorialTag,
            recommendedReason = recommendedReason,
            searchKeywords = searchKeywords,
        ),
        appDetail = AppDetail(
            appId = appId,
            packageName = packageName,
            name = name,
            description = description,
            versionName = versionName,
            developerName = developerName,
            category = category,
            ratingText = ratingText,
            sizeText = sizeText,
            lastUpdatedText = lastUpdatedText,
            compatibilitySummary = compatibilitySummary,
            permissionsSummary = permissionsSummary,
            updateSummary = updateSummary,
            apkUrl = "",
        ),
        upgradeInfo = UpgradeInfo(
            appId = appId,
            latestVersion = latestVersion,
            apkUrl = "",
            hasUpgrade = hasUpgrade,
            changelog = changelog,
        ),
    )
}
