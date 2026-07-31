package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.core.downloader.DownloadSourcePolicy
import com.xzq.appstore.data.model.AppPlatform
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogJsonParserTest {

    @Test
    fun `parseResponse 会解析目录字段与搜索关键词`() {
        val response = AppCatalogJsonParser.parseResponse(TEST_CATALOG_JSON)

        assertEquals(1, response.apps.size)
        val item = response.apps.first()
        assertEquals("nav.map", item.appId)
        assertEquals("地图导航", item.category)
        assertEquals(listOf("导航", "路况", "充电"), item.searchKeywords)
        assertEquals("asset://icons/nav_map.png", item.iconUrl)
        assertEquals("asset://banners/nav_map.png", item.bannerUrl)
        assertEquals(listOf("asset://screens/nav_map_1.png", "asset://screens/nav_map_2.png"), item.screenshotUrls)
        assertEquals("2.3.0", item.latestVersion)
        assertEquals("https://download.example.com/nav.map.apk", item.apkUrl)
        assertEquals("SHA-256", item.checksumType)
        assertEquals("abc123", item.checksumValue)
        assertEquals(DownloadSourcePolicy.DIRECT_HTTP, item.sourcePolicy)
        assertEquals(CatalogListingState.ROLLBACK, item.listingState)
        assertEquals(60, item.rolloutPercent)
        assertEquals(listOf("carappstore-test"), item.allowedChannels)
        assertEquals(listOf("carappstore-legacy"), item.blockedChannels)
        assertEquals("2.0.5", item.rollbackVersion)
        assertEquals(setOf(AppPlatform.ANDROID), item.supportedPlatforms)
    }

    @Test
    fun `parse 会映射为远端目录模型`() {
        val catalog = AppCatalogJsonParser.parse(TEST_CATALOG_JSON)

        assertEquals(1, catalog.size)
        val item = catalog.first()
        assertEquals("nav.map", item.appId)
        assertEquals("com.nio.map", item.appInfo.packageName)
        assertEquals("asset://icons/nav_map.png", item.appInfo.iconUrl)
        assertEquals("asset://banners/nav_map.png", item.appDetail.bannerUrl)
        assertEquals("https://download.example.com/nav.map.apk", item.appDetail.apkUrl)
        assertEquals("2.0.5", item.appDetail.versionName)
        assertEquals("2.0.5", item.appInfo.versionName)
        assertEquals("2.0.5", item.upgradeInfo.latestVersion)
        assertEquals(false, item.upgradeInfo.hasUpgrade)
        assertEquals("abc123", item.appDetail.checksumValue)
        assertEquals("适配座舱导航场景", item.appInfo.recommendedReason)
        assertEquals("蔚来地图团队", item.appDetail.developerName)
        assertEquals("新增沿途充电推荐", item.upgradeInfo.changelog)
        assertEquals(setOf(AppPlatform.ANDROID), item.appDetail.supportedPlatforms)
        assertEquals(true, item.appDetail.currentPlatformSupported)
    }

    @Test
    fun `parse explicit other platforms does not fall back to Android`() {
        val catalog = AppCatalogJsonParser.parse(
            """
            {
              "apps": [
                {
                  "appId": "other.platform.app",
                  "packageName": "",
                  "supportedPlatforms": ["ios", "windows", "future-os"],
                  "name": "Other Platform App",
                  "description": "Not installable on Android",
                  "versionName": "1.0.0"
                }
              ]
            }
            """.trimIndent(),
        )

        val detail = catalog.single().appDetail
        assertEquals(setOf(AppPlatform.IOS, AppPlatform.WINDOWS), detail.supportedPlatforms)
        assertEquals(false, detail.currentPlatformSupported)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse Android catalog item still requires a valid package name`() {
        AppCatalogJsonParser.parse(
            """
            {
              "apps": [
                {
                  "appId": "android.app",
                  "packageName": "",
                  "supportedPlatforms": ["android"],
                  "name": "Android App",
                  "description": "Missing package",
                  "versionName": "1.0.0"
                }
              ]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `parseResponse 会解析版本代码并规范化签名摘要`() {
        val item = AppCatalogJsonParser.parseResponse(identityCatalog()).apps.single()

        assertEquals(TEST_VERSION_CODE, item.versionCode)
        assertEquals(listOf(TEST_SIGNER.lowercase()), item.signerCertificateSha256)
    }

    @Test
    fun `parse 会把 APK 身份元数据映射到详情模型`() {
        val detail = AppCatalogJsonParser.parse(identityCatalog()).single().appDetail

        assertEquals(TEST_VERSION_CODE, detail.versionCode)
        assertEquals(listOf(TEST_SIGNER.lowercase()), detail.signerCertificateSha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseResponse 拒绝路径穿越 appId`() {
        AppCatalogJsonParser.parseResponse(identityCatalog(appId = "../../escape"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseResponse 拒绝非法包名`() {
        AppCatalogJsonParser.parseResponse(identityCatalog(packageName = "../package"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseResponse 拒绝重复 appId`() {
        AppCatalogJsonParser.parseResponse(twoItemCatalog(secondAppId = TEST_APP_ID))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseResponse 拒绝重复 packageName`() {
        AppCatalogJsonParser.parseResponse(twoItemCatalog(secondPackageName = TEST_PACKAGE_NAME))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseResponse 拒绝非法签名摘要`() {
        AppCatalogJsonParser.parseResponse(identityCatalog(signer = "not-a-sha256"))
    }

    private fun identityCatalog(
        appId: String = TEST_APP_ID,
        packageName: String = TEST_PACKAGE_NAME,
        signer: String = TEST_SIGNER,
    ): String = """
        {
          "apps": [
            {
              "appId": "$appId",
              "packageName": "$packageName",
              "name": "安全测试应用",
              "description": "测试 APK 身份元数据",
              "versionName": "2.3.0",
              "versionCode": $TEST_VERSION_CODE,
              "category": "工具",
              "editorialTag": "测试",
              "latestVersion": "2.3.0",
              "apkUrl": "https://download.example.com/security.apk",
              "signerCertificateSha256": ["$signer"],
              "hasUpgrade": false,
              "changelog": ""
            }
          ]
        }
    """.trimIndent()

    private fun twoItemCatalog(
        secondAppId: String = "second.app",
        secondPackageName: String = "com.example.second",
    ): String {
        val first = JSONObject(identityCatalog()).getJSONArray("apps").getJSONObject(0)
        val second = JSONObject(first.toString()).apply {
            put("appId", secondAppId)
            put("packageName", secondPackageName)
        }
        return JSONObject().put("apps", JSONArray().put(first).put(second)).toString()
    }

    private companion object {
        const val TEST_APP_ID = "nav.map"
        const val TEST_PACKAGE_NAME = "com.nio.map"
        const val TEST_VERSION_CODE = 230L
        const val TEST_SIGNER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        /** 测试目录响应。 */
        const val TEST_CATALOG_JSON = """
            {
              "apps": [
                {
                  "appId": "nav.map",
                  "packageName": "com.nio.map",
                  "name": "NIO Map",
                  "description": "车机地图导航",
                  "versionName": "2.1.0",
                  "category": "地图导航",
                  "editorialTag": "通勤推荐",
                  "iconUrl": "asset://icons/nav_map.png",
                  "bannerUrl": "asset://banners/nav_map.png",
                  "screenshotUrls": ["asset://screens/nav_map_1.png", "asset://screens/nav_map_2.png"],
                  "recommendedReason": "适配座舱导航场景",
                  "searchKeywords": ["导航", "路况", "充电"],
                  "developerName": "蔚来地图团队",
                  "ratingText": "4.8",
                  "sizeText": "128 MB",
                  "lastUpdatedText": "2026-04-16",
                  "compatibilitySummary": "支持 Banyan 2.4 及以上",
                  "permissionsSummary": "位置、网络、蓝牙",
                  "updateSummary": "优化弱网路径规划",
                  "latestVersion": "2.3.0",
                  "apkUrl": "https://download.example.com/nav.map.apk",
                  "checksumType": "SHA-256",
                  "checksumValue": "abc123",
                  "sourcePolicy": "DIRECT_HTTP",
                  "listingState": "ROLLBACK",
                  "rolloutPercent": 60,
                  "allowedChannels": ["carappstore-test"],
                  "blockedChannels": ["carappstore-legacy"],
                  "rollbackVersion": "2.0.5",
                  "hasUpgrade": true,
                  "changelog": "新增沿途充电推荐"
                }
              ]
            }
        """
    }
}
