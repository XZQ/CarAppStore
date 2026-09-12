package com.xzq.appstore.app

import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import com.xzq.appstore.R
import com.xzq.appstore.feature.search.SearchFragment
import com.xzq.appstore.feature.search.SearchViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import com.xzq.appstore.feature.search.R as SearchR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = App::class, qualifiers = "w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SearchProcessRestoreTest {
    @Test
    fun `fresh activity and model restore search after saved state parcel roundtrip`() {
        val app = RuntimeEnvironment.getApplication() as App
        runBlocking { app.appContainer.awaitReady() }
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        fun drain() {
            shadowOf(Looper.getMainLooper()).idle()
            controller.get().supportFragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
        }
        try {
            drain()
            controller.get().openSearch()
            drain()
            val originalFragment = controller.get().supportFragmentManager.findFragmentById(R.id.fragmentContainer) as SearchFragment
            val originalModel = ViewModelProvider(originalFragment)[SearchViewModel::class.java]
            controller.get().findViewById<EditText>(SearchR.id.etSearch).setText("保留搜索草稿")
            originalModel.selectCategory("工具")
            val state = Bundle()
            controller.pause().saveInstanceState(state).stop().destroy()
            val parcel = Parcel.obtain()
            val restoredState = try {
                state.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                Bundle.CREATOR.createFromParcel(parcel)
            } finally { parcel.recycle() }
            controller = Robolectric.buildActivity(MainActivity::class.java)
                .create(restoredState).start().restoreInstanceState(restoredState).resume().visible()
            drain()
            val restoredFragment = controller.get().supportFragmentManager.findFragmentById(R.id.fragmentContainer) as SearchFragment
            val restoredModel = ViewModelProvider(restoredFragment)[SearchViewModel::class.java]
            assertNotSame(originalModel, restoredModel)
            assertEquals("保留搜索草稿", restoredModel.uiState.value.keyword)
            assertEquals("工具", restoredModel.uiState.value.selectedCategory)
            assertEquals("保留搜索草稿", controller.get().findViewById<EditText>(SearchR.id.etSearch).text.toString())
        } finally {
            controller.pause().stop().destroy()
            runBlocking { app.appContainer.shutdown() }
        }
    }
}
