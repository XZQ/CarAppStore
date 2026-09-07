package com.xzq.appstore.app

import android.app.Application
import android.app.Notification
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DownloadForegroundServiceTest {
    @Test
    fun `notification exposes task center and pause action with progress`() {
        val service = Robolectric.buildService(DownloadForegroundService::class.java).get()
        val notification = service.buildNotification(2, 40)
        assertEquals(40, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("2"))
        assertEquals("全部暂停", notification.actions.single().title)
        assertEquals(DownloadForegroundService.ACTION_PAUSE, shadowOf(notification.actions.single().actionIntent).savedIntent.action)
        assertTrue(shadowOf(notification.contentIntent).savedIntent.getBooleanExtra(DownloadForegroundService.EXTRA_OPEN_DOWNLOADS, false))
    }

    @Test
    fun `engine waits for foreground acknowledgement and ignores old service stop`() {
        val host = AndroidDownloadExecutionHost(RuntimeEnvironment.getApplication())
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val acquire = scope.launch { host.acquire("app") }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(acquire.isCompleted)
        val first = Any()
        host.onServiceStarted(first)
        host.finishRecovery(first)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(acquire.isCompleted)
        scope.launch { host.release("app") }
        shadowOf(Looper.getMainLooper()).idle()
        val next = scope.launch { host.acquire("next") }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(host.onServiceStopped(first))
        val second = Any()
        host.onServiceStarted(second)
        host.finishRecovery(second)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(next.isCompleted)
        assertEquals(setOf("next"), host.activeAppIds.value)
        scope.launch { host.release("next") }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
