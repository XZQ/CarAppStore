package com.xzq.appstore.app

import android.app.Application
import com.xzq.appstore.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], application = Application::class)
class ReleaseNavigationTest {
    @Test
    fun `release developer route returns before touching navigation or services`() {
        assertFalse(BuildConfig.DEBUG)
        MainActivity().openDeveloperSettings()
        assertNull(DeveloperSettingsDestination.create())
    }

    @Test
    fun `release runtime excludes developer feature classes`() {
        try {
            Class.forName("com.xzq.appstore.feature.debug.DeveloperSettingsFragment")
            fail("Release must not include developer settings")
        } catch (_: ClassNotFoundException) { }
    }
}
