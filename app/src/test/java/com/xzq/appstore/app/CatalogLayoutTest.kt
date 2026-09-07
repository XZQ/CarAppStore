package com.xzq.appstore.app

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.xzq.appstore.common.ui.CatalogAppAdapter
import com.xzq.appstore.common.ui.CatalogGridLayoutManager
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.domain.state.PrimaryAction
import com.xzq.appstore.feature.search.databinding.FragmentSearchBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CatalogLayoutTest {
    @Test @Config(qualifiers = "w360dp-h800dp")
    fun `phone catalog recycles and reaches final result`() = checkCatalog(1)

    @Test @Config(qualifiers = "sw600dp-w800dp-h1000dp")
    fun `tablet catalog recycles and reaches final result`() = checkCatalog(2)

    @Test @Config(qualifiers = "sw900dp-w1200dp-h900dp-land")
    fun `large window catalog recycles and reaches final result`() = checkCatalog(3)

    @Test @Config(qualifiers = "sw900dp-w1200dp-h900dp-land")
    fun `wide window with constrained pane uses available width`() = checkCatalog(1, paneWidthDp = 500)

    private fun checkCatalog(columns: Int, paneWidthDp: Int? = null) {
        val context = RuntimeEnvironment.getApplication()
        val binding = FragmentSearchBinding.inflate(LayoutInflater.from(context))
        val recycler = binding.listCatalogResults
        recycler.layoutManager = CatalogGridLayoutManager(context)
        val adapter = CatalogAppAdapter({}, {})
        recycler.adapter = adapter
        adapter.submitList((1..1000).map { AppViewData("app$it", "App $it", "", "1", stateText = "下载", primaryAction = PrimaryAction.DOWNLOAD) })
        val width = paneWidthDp?.let { (it * context.resources.displayMetrics.density).toInt() }
            ?: context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        fun layout() {
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
        }
        layout()
        assertTrue(recycler.height > 0)
        assertEquals(columns, (recycler.layoutManager as GridLayoutManager).spanCount)
        assertTrue(recycler.childCount in 1..60)
        assertEquals(1000, adapter.itemCount)
        recycler.scrollToPosition(999)
        layout()
        assertNotNull(recycler.findViewHolderForAdapterPosition(999))
        recycler.adapter = null
    }
}
