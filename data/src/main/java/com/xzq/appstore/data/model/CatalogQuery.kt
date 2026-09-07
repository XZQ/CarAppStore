package com.xzq.appstore.data.model

import com.xzq.appstore.common.navigation.CatalogSection

data class CatalogQuery(
    val keyword: String = "",
    val section: CatalogSection = CatalogSection.Software,
    val category: String? = null,
)
