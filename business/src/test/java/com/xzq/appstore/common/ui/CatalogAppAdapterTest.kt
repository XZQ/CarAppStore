package com.xzq.appstore.common.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ApplicationProvider
import com.xzq.appstore.common.R
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.domain.state.PrimaryAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CatalogAppAdapterTest {
    @Test
    fun `progress payload keeps icon and click uses latest bound action`() {
        var clicked: AppViewData? = null
        val adapter = CatalogAppAdapter({ clicked = it }, {})
        val parent = RecyclerView(ApplicationProvider.getApplicationContext<Context>()).apply { layoutManager = LinearLayoutManager(context) }
        val holder = adapter.onCreateViewHolder(parent, 0)
        val before = AppViewData("app", "App", "", "1", stateText = "下载", primaryAction = PrimaryAction.DOWNLOAD)
        holder.bind(before, 0, false)
        val icon = holder.itemView.findViewById<ImageView>(R.id.ivIcon)
        val drawable = ColorDrawable(0xffff0000.toInt())
        icon.setImageDrawable(drawable)
        val after = before.copy(progress = 35, stateText = "下载中", primaryAction = PrimaryAction.PAUSE)
        assertNotNull(CatalogAppAdapter.Diff.getChangePayload(before, after))
        holder.bind(after, 0, true)
        assertSame(drawable, icon.drawable)
        assertEquals(35, holder.itemView.findViewById<ProgressBar>(R.id.progressDownload).progress)
        holder.itemView.findViewById<Button>(R.id.btnPrimary).performClick()
        assertEquals(after, clicked)
        assertNull(CatalogAppAdapter.Diff.getChangePayload(before, after.copy(name = "Changed")))
    }
}
