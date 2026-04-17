package com.nio.appstore.data.datasource.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("2.3.0", item.latestVersion)
    }

    @Test
    fun `parse 会映射为远端目录模型`() {
        val catalog = AppCatalogJsonParser.parse(TEST_CATALOG_JSON)

        assertEquals(1, catalog.size)
        val item = catalog.first()
        assertEquals("nav.map", item.appId)
        assertEquals("com.nio.map", item.appInfo.packageName)
        assertEquals("适配座舱导航场景", item.appInfo.recommendedReason)
        assertEquals("蔚来地图团队", item.appDetail.developerName)
        assertEquals("新增沿途充电推荐", item.upgradeInfo.changelog)
        assertTrue(item.upgradeInfo.hasUpgrade)
    }

    private companion object {
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
                  "hasUpgrade": true,
                  "changelog": "新增沿途充电推荐"
                }
              ]
            }
        """
    }
}
