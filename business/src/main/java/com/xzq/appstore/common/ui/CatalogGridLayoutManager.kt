package com.xzq.appstore.common.ui

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** 按列表实际可用宽度排版，适配侧栏、分屏和窗口缩放。 */
class CatalogGridLayoutManager(context: Context) : GridLayoutManager(context, 1) {
    private val minimumCardWidth = (320 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        spanCount = ((width - paddingLeft - paddingRight) / minimumCardWidth).coerceIn(1, 3)
        super.onLayoutChildren(recycler, state)
    }
}
