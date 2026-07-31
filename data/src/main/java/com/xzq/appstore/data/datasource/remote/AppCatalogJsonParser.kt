package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.core.downloader.DownloadSourcePolicy
import com.xzq.appstore.data.model.AppPlatform
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppCatalogJsonParser 负责解析商店目录 JSON 文本。
 */
object AppCatalogJsonParser {
    /** 解析指定文本内容。 */
    fun parse(rawText: String): List<RemoteCatalogItem> {
        return parseResponse(rawText).apps.map { it.toRemoteCatalogItem() }
    }

    /** 解析指定文本内容为目录响应。 */
    fun parseResponse(rawText: String): AppCatalogResponse {
        val root = JSONObject(rawText)
        val apps = root.optJSONArray("apps") ?: JSONArray()
        return AppCatalogResponse(
            apps = AppCatalogValidator.validate(List(apps.length()) { index -> parseItem(apps.getJSONObject(index)) }),
        )
    }

    /** 解析单个目录项。 */
    private fun parseItem(json: JSONObject): AppCatalogItemResponse {
        val appId = json.optString("appId")
        return AppCatalogItemResponse(
            appId = appId,
            packageName = json.optString("packageName"),
            supportedPlatforms = parseSupportedPlatforms(json),
            name = json.optString("name"),
            description = json.optString("description"),
            versionName = json.optString("versionName"),
            versionCode = json.optLong("versionCode", 0L),
            signerCertificateSha256 = parseSignerCertificateSha256(json.optJSONArray("signerCertificateSha256")),
            category = json.optString("category"),
            editorialTag = json.optString("editorialTag"),
            iconText = json.optString("iconText", json.optString("name").take(1)),
            heroText = json.optString("heroText", json.optString("recommendedReason")),
            iconUrl = json.optString("iconUrl"),
            bannerUrl = json.optString("bannerUrl"),
            screenshotUrls = parseStringList(json.optJSONArray("screenshotUrls")),
            recommendedReason = json.optString("recommendedReason"),
            searchKeywords = parseStringList(json.optJSONArray("searchKeywords")),
            developerName = json.optString("developerName"),
            ratingText = json.optString("ratingText"),
            sizeText = json.optString("sizeText"),
            lastUpdatedText = json.optString("lastUpdatedText"),
            compatibilitySummary = json.optString("compatibilitySummary"),
            permissionsSummary = json.optString("permissionsSummary"),
            updateSummary = json.optString("updateSummary"),
            latestVersion = json.optString("latestVersion", json.optString("versionName")),
            apkUrl = json.optString("apkUrl"),
            checksumType = json.optString("checksumType").ifBlank { null },
            checksumValue = json.optString("checksumValue").ifBlank { null },
            sourcePolicy = parseSourcePolicy(json.optString("sourcePolicy")),
            listingState = parseListingState(json.optString("listingState")),
            rolloutPercent = json.optInt("rolloutPercent", 100),
            allowedChannels = parseStringList(json.optJSONArray("allowedChannels")),
            blockedChannels = parseStringList(json.optJSONArray("blockedChannels")),
            rollbackVersion = json.optString("rollbackVersion"),
            hasUpgrade = json.optBoolean("hasUpgrade"),
            changelog = json.optString("changelog"),
        )
    }

    /** 把 JSON 数组转换成字符串列表。 */
    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) {
            return emptyList()
        }
        return List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }

    /**
     * 旧目录没有平台字段时保持 Android 兼容；显式字段中的未知平台会被忽略，
     * 且不会回退成 Android，避免旧客户端误下载未来平台的安装包。
     */
    private fun parseSupportedPlatforms(json: JSONObject): Set<AppPlatform> {
        if (!json.has("supportedPlatforms")) {
            return setOf(AppPlatform.ANDROID)
        }
        return parseStringList(json.optJSONArray("supportedPlatforms"))
            .mapNotNull(AppPlatform::fromWireValue)
            .toSet()
    }

    /** 规范化证书摘要，避免大小写或重复值造成不一致。 */
    private fun parseSignerCertificateSha256(array: JSONArray?): List<String> {
        return parseStringList(array).map { it.trim().lowercase() }.distinct()
    }

    private fun parseSourcePolicy(raw: String): DownloadSourcePolicy? {
        if (raw.isBlank()) {
            return null
        }
        return runCatching { DownloadSourcePolicy.valueOf(raw.trim().uppercase()) }.getOrNull()
    }

    private fun parseListingState(raw: String): CatalogListingState {
        if (raw.isBlank()) {
            return CatalogListingState.ACTIVE
        }
        return runCatching { CatalogListingState.valueOf(raw.trim().uppercase()) }.getOrDefault(CatalogListingState.ACTIVE)
    }
}
