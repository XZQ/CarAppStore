package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.core.downloader.DownloadSourcePolicy
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.AppInfo
import com.xzq.appstore.data.model.UpgradeInfo

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
    /** APK manifest 中的版本代码，旧目录未提供时为 0。 */
    val versionCode: Long = 0L,
    /** 允许安装的 APK 签名证书 SHA-256 摘要。 */
    val signerCertificateSha256: List<String> = emptyList(),
    /** 分类。 */
    val category: String,
    /** 运营标签。 */
    val editorialTag: String,
    val iconText: String,
    val heroText: String,
    val iconUrl: String = "",
    val bannerUrl: String = "",
    val screenshotUrls: List<String> = emptyList(),
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
    val apkUrl: String = "",
    val checksumType: String? = null,
    val checksumValue: String? = null,
    val sourcePolicy: DownloadSourcePolicy? = null,
    val listingState: CatalogListingState = CatalogListingState.ACTIVE,
    val rolloutPercent: Int = 100,
    val allowedChannels: List<String> = emptyList(),
    val blockedChannels: List<String> = emptyList(),
    val rollbackVersion: String = "",
    /** 是否可升级。 */
    val hasUpgrade: Boolean,
    /** 升级变更摘要。 */
    val changelog: String,
)

/**
 * 把目录响应项转换成远端目录领域模型。
 */
fun AppCatalogItemResponse.toRemoteCatalogItem(): RemoteCatalogItem {
    val governance = AppCatalogGovernance(
        listingState = listingState,
        rolloutPercent = rolloutPercent,
        allowedChannels = allowedChannels,
        blockedChannels = blockedChannels,
        rollbackVersion = rollbackVersion,
    )
    val effectiveVersion = governance.effectiveVersion(versionName)
    val effectiveLatestVersion = governance.effectiveVersion(latestVersion)
    val effectiveHasUpgrade = hasUpgrade && listingState != CatalogListingState.ROLLBACK
    return RemoteCatalogItem(
        appId = appId,
        appInfo = AppInfo(
            appId = appId,
            packageName = packageName,
            name = name,
            description = description,
            versionName = effectiveVersion,
            category = category,
            editorialTag = editorialTag,
            iconText = iconText,
            heroText = heroText,
            iconUrl = iconUrl,
            bannerUrl = bannerUrl,
            screenshotUrls = screenshotUrls,
            recommendedReason = recommendedReason,
            searchKeywords = searchKeywords,
        ),
        appDetail = AppDetail(
            appId = appId,
            packageName = packageName,
            name = name,
            description = description,
            versionName = effectiveVersion,
            versionCode = versionCode,
            signerCertificateSha256 = signerCertificateSha256,
            developerName = developerName,
            category = category,
            iconText = iconText,
            heroText = heroText,
            iconUrl = iconUrl,
            bannerUrl = bannerUrl,
            screenshotUrls = screenshotUrls,
            ratingText = ratingText,
            sizeText = sizeText,
            lastUpdatedText = lastUpdatedText,
            compatibilitySummary = compatibilitySummary,
            permissionsSummary = permissionsSummary,
            updateSummary = updateSummary,
            apkUrl = apkUrl,
            checksumType = checksumType,
            checksumValue = checksumValue,
            sourcePolicy = sourcePolicy ?: DownloadSourcePolicy.FALLBACK_SIMULATED,
        ),
        upgradeInfo = UpgradeInfo(
            appId = appId,
            latestVersion = effectiveLatestVersion,
            apkUrl = apkUrl,
            hasUpgrade = effectiveHasUpgrade,
            changelog = changelog,
        ),
        governance = governance,
    )
}
