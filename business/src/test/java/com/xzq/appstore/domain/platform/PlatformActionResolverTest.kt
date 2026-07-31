package com.xzq.appstore.domain.platform

import com.xzq.appstore.domain.state.PrimaryAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformActionResolverTest {
    @Test
    fun `unsupported platform blocks actions that create an installation artifact`() {
        val blockedActions = listOf(
            PrimaryAction.DOWNLOAD,
            PrimaryAction.RESUME,
            PrimaryAction.INSTALL,
            PrimaryAction.UPGRADE,
            PrimaryAction.RETRY_DOWNLOAD,
            PrimaryAction.RETRY_INSTALL,
        )

        blockedActions.forEach { action ->
            assertEquals(PrimaryAction.UNSUPPORTED, resolvePlatformPrimaryAction(action, currentPlatformSupported = false))
        }
    }

    @Test
    fun `unsupported platform still allows open pause and in-flight disabled states`() {
        assertEquals(PrimaryAction.OPEN, resolvePlatformPrimaryAction(PrimaryAction.OPEN, currentPlatformSupported = false))
        assertEquals(PrimaryAction.PAUSE, resolvePlatformPrimaryAction(PrimaryAction.PAUSE, currentPlatformSupported = false))
        assertEquals(PrimaryAction.DISABLED, resolvePlatformPrimaryAction(PrimaryAction.DISABLED, currentPlatformSupported = false))
    }
}
