package com.xzq.appstore.app

import android.os.Looper
import android.widget.Button
import com.xzq.appstore.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = App::class, qualifiers = "w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
class MainNavigationStateTest {
    @Test fun `back navigation and recreation restore selected destination and home clears stale history`() {
        val app = RuntimeEnvironment.getApplication() as App
        runBlocking { app.appContainer.awaitReady() }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        fun drain() { shadowOf(Looper.getMainLooper()).idle(); controller.get().supportFragmentManager.executePendingTransactions() }
        try {
            drain()
            var activity = controller.get()
            activity.openSearch(); drain()
            assertTrue(activity.findViewById<Button>(R.id.btnNavDownload).isSelected)
            activity.openDetail("test.app"); drain()
            activity.supportFragmentManager.popBackStackImmediate(); drain()
            assertTrue(activity.findViewById<Button>(R.id.btnNavDownload).isSelected)
            controller.recreate(); drain()
            activity = controller.get()
            assertTrue(activity.findViewById<Button>(R.id.btnNavDownload).isSelected)
            activity.openHome(); drain()
            assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
            assertTrue(activity.findViewById<Button>(R.id.btnNavHome).isSelected)
        } finally {
            controller.pause().stop().destroy()
            runBlocking { app.appContainer.shutdown() }
        }
    }
}
