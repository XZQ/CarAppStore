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
    Game(argument = "game", title = "游戏", searchHint = "搜索游戏", heroTitle = "发现游戏", heroSubtitle = "仅展示目录中归类为游戏的应用", firstSectionTitle = "游戏列表"),
    Category(argument = "category", title = "分类", searchHint = "按分类发现应用", heroTitle = "分类导航", heroSubtitle = "影音、出行、办公、娱乐一屏看完", firstSectionTitle = "分类推荐"),
    Rank(argument = "rank", title = "排行", searchHint = "搜索榜单应用", heroTitle = "评分排行", heroSubtitle = "按目录评分从高到低排列，未评分应用不入榜", firstSectionTitle = "评分榜单"),
    Essential(argument = "essential", title = "必备", searchHint = "搜索必备应用", heroTitle = "装机必备", heroSubtitle = "导航、音乐、办公和安全服务", firstSectionTitle = "必备清单"),
    Activity(argument = "activity", title = "活动", searchHint = "搜索活动应用", heroTitle = "应用活动", heroSubtitle = "展示目录中标注活动的应用", firstSectionTitle = "活动应用"),
    ;

    companion object {
        fun from(argument: String?): CatalogPage = entries.firstOrNull { it.argument == argument } ?: Software
    }
}
