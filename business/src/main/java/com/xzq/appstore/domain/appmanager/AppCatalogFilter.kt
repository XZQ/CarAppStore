package com.xzq.appstore.domain.appmanager

import com.xzq.appstore.common.navigation.CatalogSection
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.data.model.CatalogQuery

/** 使用目录事实筛选和排序；未提供评分的应用不进入评分榜。 */
object AppCatalogFilter {
    fun select(apps: List<AppViewData>, query: CatalogQuery): List<AppViewData> {
        val matches = apps.distinctBy { it.appId }.filter { app ->
            (query.category == null || app.category == query.category) && when (query.section) {
                CatalogSection.Software -> !isGame(app)
                CatalogSection.Game -> isGame(app)
                CatalogSection.Category -> true
                CatalogSection.Rank -> app.rating?.let { it.isFinite() && it in 0.0..5.0 } == true
                CatalogSection.Essential -> app.editorialTag.contains("必备")
                CatalogSection.Activity -> app.editorialTag.contains("活动")
            }
        }
        return when (query.section) {
            CatalogSection.Rank -> matches.sortedWith(compareByDescending<AppViewData> { it.rating }.thenBy { it.appId })
            CatalogSection.Category -> matches.sortedWith(compareBy<AppViewData> { it.category }.thenBy { it.name }.thenBy { it.appId })
            else -> matches
        }
    }

    private fun isGame(app: AppViewData): Boolean = app.category.contains("游戏") || app.category.equals("game", true) || app.category.equals("games", true)
}
