package com.xzq.appstore.domain.appmanager

import com.xzq.appstore.common.navigation.CatalogSection
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.data.model.CatalogQuery
import com.xzq.appstore.domain.state.PrimaryAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogFilterTest {
    @Test
    fun `目录去重且不补齐或截断结果`() {
        val single = app("single")
        assertEquals(listOf(single), select(listOf(single, single), CatalogSection.Category))
        val apps = (1..20).map { app("app$it") }
        assertEquals(apps.toSet(), select(apps, CatalogSection.Software).toSet())
    }

    @Test
    fun `软件游戏和分类按真实分类字段筛选`() {
        val apps = listOf(app("game", "角色游戏"), app("office", "办公"), app("music", "音乐"))
        assertEquals(listOf(apps[0]), select(apps, CatalogSection.Game))
        assertEquals(apps.drop(1), select(apps, CatalogSection.Software))
        assertEquals(listOf(apps[1]), AppCatalogFilter.select(apps, CatalogQuery(section = CatalogSection.Category, category = "办公")))
    }

    @Test
    fun `评分榜忽略未知和非法评分并稳定降序排列`() {
        val apps = listOf(app("unknown"), app("b", rating = 4.8), app("a", rating = 4.8), app("c", rating = 4.1), app("invalid", rating = Double.NaN))
        assertEquals(listOf("a", "b", "c"), select(apps, CatalogSection.Rank).map { it.appId })
    }

    @Test
    fun `必备与活动只展示有对应目录标签的应用`() {
        val apps = listOf(app("normal"), app("essential", tag = "装机必备"), app("activity", tag = "活动"))
        assertEquals(listOf(apps[1]), select(apps, CatalogSection.Essential))
        assertEquals(listOf(apps[2]), select(apps, CatalogSection.Activity))
    }

    private fun select(apps: List<AppViewData>, section: CatalogSection) = AppCatalogFilter.select(apps, CatalogQuery(section = section))

    private fun app(id: String, category: String = "办公", rating: Double? = null, tag: String = "") = AppViewData(
        appId = id, name = id, description = "", versionName = "1", stateText = "", primaryAction = PrimaryAction.DOWNLOAD,
        category = category, rating = rating, editorialTag = tag,
    )
}
