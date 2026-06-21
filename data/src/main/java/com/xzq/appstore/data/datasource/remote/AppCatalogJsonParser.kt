package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.core.downloader.DownloadSourcePolicy
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
            apps = List(apps.length()) { index -> parseItem(apps.getJSONObject(index)) },
        )
    }

    /** 解析单个目录项。 */
    private fun parseItem(json: JSONObject): AppCatalogItemResponse {
        val appId = json.optString("appId")
        return AppCatalogItemResponse(
            appId = appId,
            packageName = json.optString("packageName"),
            name = json.optString("name"),
            description = json.optString("description"),
            versionName = json.optString("versionName"),
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
        if (array == null) return emptyList()
        return List(array.length()) { index -> array.optString(index) }
            .filter { it.isNotBlank() }
    }

    private fun parseSourcePolicy(raw: String): DownloadSourcePolicy? {
        if (raw.isBlank()) return null
        return runCatching { DownloadSourcePolicy.valueOf(raw.trim().uppercase()) }.getOrNull()
    }

    private fun parseListingState(raw: String): CatalogListingState {
        if (raw.isBlank()) return CatalogListingState.ACTIVE
        return runCatching { CatalogListingState.valueOf(raw.trim().uppercase()) }
            .getOrDefault(CatalogListingState.ACTIVE)
    }
}
