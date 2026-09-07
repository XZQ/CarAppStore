package com.xzq.appstore.data.datasource.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogGovernanceTest {

    @Test
    fun `hidden and removed apps are not visible`() {
        assertFalse(AppCatalogGovernance(listingState = CatalogListingState.HIDDEN).isVisible("app", "carappstore-test", "installation", "1"))
        assertFalse(AppCatalogGovernance(listingState = CatalogListingState.REMOVED).isVisible("app", "carappstore-test", "installation", "1"))
    }

    @Test
    fun `allowed and blocked channels control visibility`() {
        val governance = AppCatalogGovernance(allowedChannels = listOf("carappstore-test"), blockedChannels = listOf("carappstore-legacy"))

        assertTrue(governance.isVisible("app", "carappstore-test", "installation", "1"))
        assertFalse(governance.isVisible("app", "carappstore-prod", "installation", "1"))
        assertFalse(governance.isVisible("app", "carappstore-legacy", "installation", "1"))
    }

    @Test
    fun `rollout percent zero hides app`() {
        assertFalse(AppCatalogGovernance(rolloutPercent = 0).isVisible("app", "carappstore-test", "installation", "1"))
        assertTrue(AppCatalogGovernance(rolloutPercent = 100).isVisible("app", "carappstore-test", "installation", "1"))
    }

    @Test
    fun `rollout is stable per installation release and distributed across installations`() {
        val governance = AppCatalogGovernance(rolloutPercent = 50)
        val selected = (1..1000).filter { governance.isVisible("app", "test", "install-$it", "code:100") }.toSet()
        assertTrue(selected.size in 400..600)
        repeat(3) {
            assertEquals(selected, (1..1000).filter { governance.isVisible("app", "test", "install-$it", "code:100") }.toSet())
        }
        val expanded = governance.copy(rolloutPercent = 75)
        assertTrue(selected.all { expanded.isVisible("app", "test", "install-$it", "code:100") })
        assertTrue((1..20).any { governance.rolloutBucket("install-$it", "app", "code:100") != governance.rolloutBucket("install-$it", "app", "code:101") })
    }

    @Test
    fun `rollback state overrides displayed version`() {
        val governance = AppCatalogGovernance(listingState = CatalogListingState.ROLLBACK, rollbackVersion = "1.9.0")

        assertEquals("1.9.0", governance.effectiveVersion("2.0.0"))
    }
}
