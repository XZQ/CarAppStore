package com.xzq.appstore.common.base

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = GateApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class BaseFragmentInitializationTest {
    @Test
    fun `page waits for initialization and binds once for each live view`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        val container = FrameLayout(activity).apply { id = View.generateViewId() }
        activity.setContentView(container)
        val fragment = GateFragment()
        activity.supportFragmentManager.beginTransaction().add(container.id, fragment).commitNow()
        assertEquals(0, fragment.bindCount)
        activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        ApplicationProvider.getApplicationContext<GateApplication>().ready.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fragment.bindCount)
        activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, fragment.bindCount)
        controller.pause().stop().destroy()
    }
}

class GateFragment : BaseFragment() {
    var bindCount = 0
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = View(requireContext())
    override fun onServicesReady(view: View, savedInstanceState: Bundle?) { bindCount++ }
}

class GateApplication : Application(), AppContainerProvider {
    val ready = CompletableDeferred<Unit>()
    override val appServices: AppServices = object : AppServices {
        override suspend fun awaitReady() { ready.await() }
        override val downloadEnvironmentProvider get() = error("Unexpected service access")
        override val appManager get() = error("Unexpected service access")
        override val stateCenter get() = error("Unexpected service access")
        override val downloadManager get() = error("Unexpected service access")
        override val installManager get() = error("Unexpected service access")
        override val upgradeManager get() = error("Unexpected service access")
        override val policyCenter get() = error("Unexpected service access")
        override val installSessionStore get() = error("Unexpected service access")
        override val installUserActionDispatcher get() = error("Unexpected service access")
        override val eventTracker get() = error("Unexpected service access")
    }
}
