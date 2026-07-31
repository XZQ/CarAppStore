package com.xzq.appstore.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformCapabilitiesTest {
    @Test
    fun `wire value parsing is case insensitive and forward compatible`() {
        assertEquals(AppPlatform.ANDROID, AppPlatform.fromWireValue(" Android "))
        assertEquals(AppPlatform.MACOS, AppPlatform.fromWireValue("macOS"))
        assertNull(AppPlatform.fromWireValue("future-os"))
    }

    @Test
    fun `Android client only supports catalog entries that declare Android`() {
        val capabilities = ClientPlatformCapabilities()

        assertTrue(capabilities.supports(setOf(AppPlatform.ANDROID, AppPlatform.WINDOWS)))
        assertFalse(capabilities.supports(setOf(AppPlatform.IOS, AppPlatform.MACOS)))
        assertFalse(capabilities.supports(emptySet()))
    }
}
