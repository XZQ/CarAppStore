package com.xzq.appstore.feature.home

import com.xzq.appstore.common.ui.CatalogAppAdapter
import com.xzq.appstore.data.model.AppViewData

class HomeAdapter(
    onPrimaryClick: (AppViewData) -> Unit,
    onDetailClick: (AppViewData) -> Unit,
    showRank: Boolean = false,
    compact: Boolean = false,
) : CatalogAppAdapter(onPrimaryClick, onDetailClick, showRank, compact)
