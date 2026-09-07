package com.xzq.appstore.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.xzq.appstore.R
import com.xzq.appstore.common.ui.CatalogAppAdapter
import com.xzq.appstore.common.ui.CatalogGridLayoutManager
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.databinding.ActivityMainBinding
import com.xzq.appstore.domain.state.PrimaryAction
import com.xzq.appstore.feature.search.databinding.FragmentSearchBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.xzq.appstore.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShellCompatibilityTest {
    @Test fun `system bars cutout and keyboard use maximum inset without accumulating padding`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity).apply { setPadding(10, 20, 10, 20) }
        activity.setContentView(root)
        var imeVisible = false
        root.applyShellInsets { imeVisible = it }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(30, 0, 0, 0))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
            .setVisible(WindowInsetsCompat.Type.ime(), true).build()
        repeat(2) { ViewCompat.dispatchApplyWindowInsets(root, insets) }
        assertEquals(40, root.paddingLeft)
        assertEquals(44, root.paddingTop)
        assertEquals(320, root.paddingBottom)
        assertTrue(imeVisible)
        ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder(insets)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE).setVisible(WindowInsetsCompat.Type.ime(), false).build())
        assertEquals(68, root.paddingBottom)
        assertFalse(imeVisible)
    }

    @Test fun `light and dark palettes keep text contrast and select system bar icon appearance`() {
        for (night in listOf(false, true)) {
            val context = configuredContext(night = night)
            assertEquals(!night, context.resources.getBoolean(R.bool.light_system_bars))
            val backgrounds = listOf(CommonR.color.car_bg, CommonR.color.car_card, CommonR.color.car_surface_light)
            val foregrounds = listOf(CommonR.color.car_text_primary, CommonR.color.car_text_secondary, CommonR.color.car_text_hint, CommonR.color.car_accent)
            backgrounds.forEach { bg -> foregrounds.forEach { fg ->
                val contrast = ColorUtils.calculateContrast(ContextCompat.getColor(context, fg), ContextCompat.getColor(context, bg))
                assertTrue("night=$night foreground=$fg background=$bg contrast=$contrast", contrast >= 4.5)
            } }
            listOf(CommonR.color.car_success, CommonR.color.car_warning).forEach { bg ->
                assertTrue(ColorUtils.calculateContrast(ContextCompat.getColor(context, CommonR.color.car_on_bright_action),
                    ContextCompat.getColor(context, bg)) >= 4.5)
            }
        }
    }

    @Test @Config(qualifiers = "w360dp-h800dp")
    fun `large text search retains scrollable results and a full action button`() {
        val context = configuredContext(fontScale = 2f)
        val binding = FragmentSearchBinding.inflate(LayoutInflater.from(context))
        binding.tvCatalogTitle.visibility = View.GONE
        binding.tvSearchSubtitle.visibility = View.GONE
        binding.etSearch.setText("很长的搜索关键词")
        binding.tvResultTitle.text = "找到 100 个应用"
        val adapter = CatalogAppAdapter({}, {})
        binding.listCatalogResults.layoutManager = CatalogGridLayoutManager(context)
        binding.listCatalogResults.adapter = adapter
        adapter.submitList((1..100).map { AppViewData("app$it", "这是一个较长的应用名称 $it", "", "1", stateText = "等待下载", primaryAction = PrimaryAction.DOWNLOAD) })
        layout(binding.root, context, 360, 480)
        val list = binding.listCatalogResults
        assertTrue(list.height > 0)
        val row = requireNotNull(list.findViewHolderForAdapterPosition(0)).itemView
        assertEquals(LinearLayout.VERTICAL, row.findViewById<LinearLayout>(CommonR.id.cardBody).orientation)
        val action = row.findViewById<View>(CommonR.id.btnPrimary)
        assertTrue(action.height >= 48 * context.resources.displayMetrics.density)
        assertTrue(action.width > 0)
        list.scrollToPosition(99)
        layout(binding.root, context, 360, 480)
        assertNotNull(list.findViewHolderForAdapterPosition(99))
    }

    @Test @Config(qualifiers = "sw900dp-w1200dp-h900dp-land")
    fun `desktop rail scrolls when keyboard or large text reduces available height`() {
        val context = configuredContext(fontScale = 2f)
        val binding = ActivityMainBinding.inflate(LayoutInflater.from(context))
        layout(binding.root, context, 1200, 500)
        val rail = binding.root.findViewById<NestedScrollView>(R.id.navigationRail)
        assertTrue(rail.getChildAt(0).height > rail.height)
        val download = binding.root.findViewById<View>(R.id.btnNavDesktopDownload)
        rail.smoothScrollTo(0, rail.getChildAt(0).height)
        assertTrue(download.height >= 48 * context.resources.displayMetrics.density)
    }

    private fun configuredContext(fontScale: Float = 1f, night: Boolean = false): Context {
        val app = RuntimeEnvironment.getApplication()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }).also { it.setTheme(R.style.Theme_CarAppStore) }
    }

    private fun layout(view: View, context: Context, width: Int, height: Int) {
        val density = context.resources.displayMetrics.density
        val w = (width * density).toInt()
        val h = (height * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
    }
}
