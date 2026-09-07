package com.xzq.appstore.core.installer

import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class InstallUserActionDispatcherTest {
    @Test
    fun `confirmation survives absence of collectors and is consumed once`() = runBlocking {
        val dispatcher = InstallUserActionDispatcher { 0 }
        dispatcher.dispatch(1, Intent("confirm"))
        assertEquals(setOf(1), dispatcher.actions.first().keys)
        var launches = 0
        assertTrue(dispatcher.launchPending(1) { launches++ })
        dispatcher.dispatch(1, Intent("duplicate"))
        assertFalse(dispatcher.launchPending(1) { launches++ })
        assertEquals(1, launches)
        assertTrue(dispatcher.actions.first().isEmpty())
    }

    @Test
    fun `failed launch can retry while other sessions stay independent`() {
        val dispatcher = InstallUserActionDispatcher { 0 }
        dispatcher.dispatch(1, Intent())
        dispatcher.dispatch(2, Intent())
        try {
            dispatcher.launchPending(1) { throw SecurityException("blocked") }
            fail("launch should throw")
        } catch (_: SecurityException) { }
        assertEquals(setOf(1, 2), dispatcher.actions.value.keys)
        assertTrue(dispatcher.launchPending(1) {})
        dispatcher.clear(2)
        assertFalse(dispatcher.launchPending(2) { fail("finished session") })
    }

    @Test
    fun `expired confirmation cannot launch after returning to foreground`() {
        var now = 0L
        val dispatcher = InstallUserActionDispatcher { now }
        dispatcher.dispatch(1, Intent(), lifetimeMs = 100)
        now = 100
        assertFalse(dispatcher.launchPending(1) { fail("expired session") })
        assertTrue(dispatcher.actions.value.isEmpty())
    }
}
