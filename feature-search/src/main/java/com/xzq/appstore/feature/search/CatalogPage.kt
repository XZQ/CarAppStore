package com.xzq.appstore.feature.search

enum class CatalogPage(
    val argument: String,
    val title: String,
    val searchHint: String,
    val heroTitle: String,
    val heroSubtitle: String,
    val firstSectionTitle: String,
) {
    Software(argument = "software", title = "软件", searchHint = "搜索软件或效率工具", heroTitle = "效率工具精选", heroSubtitle = "视频剪辑、办公协作与常用工具", firstSectionTitle = "猜你想找"),
    Game(argument = "game", title = "游戏", searchHint = "搜索游戏或娱乐应用", heroTitle = "原神 4.7 版本", heroSubtitle = "全新区服「幻想真境」开放", firstSectionTitle = "热门榜单"),
    Category(argument = "category", title = "分类", searchHint = "按分类发现应用", heroTitle = "分类导航", heroSubtitle = "影音、出行、办公、娱乐一屏看完", firstSectionTitle = "分类推荐"),
    Rank(argument = "rank", title = "排行", searchHint = "搜索榜单应用", heroTitle = "热门排行", heroSubtitle = "下载、评分和更新热度综合排序", firstSectionTitle = "热门榜单"),
    Essential(argument = "essential", title = "必备", searchHint = "搜索必备应用", heroTitle = "装机必备", heroSubtitle = "导航、音乐、办公和安全服务", firstSectionTitle = "必备清单"),
    Activity(argument = "activity", title = "活动", searchHint = "搜索活动应用", heroTitle = "限时活动", heroSubtitle = "周末狂欢，下载赢好礼", firstSectionTitle = "活动推荐"),
    ;

    companion object {
        fun from(argument: String?): CatalogPage = entries.firstOrNull { it.argument == argument } ?: Software
    }
}
