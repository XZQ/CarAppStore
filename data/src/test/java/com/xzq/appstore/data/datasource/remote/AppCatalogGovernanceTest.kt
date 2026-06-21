package com.xzq.appstore.data.datasource.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogGovernanceTest {

    @Test
    fun `hidden and removed apps are not visible`() {
        assertFalse(AppCatalogGovernance(listingState = CatalogListingState.HIDDEN).isVisible("app", "carappstore-test"))
        assertFalse(AppCatalogGovernance(listingState = CatalogListingState.REMOVED).isVisible("app", "carappstore-test"))
    }

    @Test
    fun `allowed and blocked channels control visibility`() {
        val governance = AppCatalogGovernance(
            allowedChannels = listOf("carappstore-test"),
            blockedChannels = listOf("carappstore-legacy"),
        )

        assertTrue(governance.isVisible("app", "carappstore-test"))
        assertFalse(governance.isVisible("app", "carappstore-prod"))
        assertFalse(governance.isVisible("app", "carappstore-legacy"))
    }

    @Test
    fun `rollout percent zero hides app`() {
        assertFalse(AppCatalogGovernance(rolloutPercent = 0).isVisible("app", "carappstore-test"))
        assertTrue(AppCatalogGovernance(rolloutPercent = 100).isVisible("app", "carappstore-test"))
    }

    @Test
    fun `rollback state overrides displayed version`() {
        val governance = AppCatalogGovernance(
            listingState = CatalogListingState.ROLLBACK,
            rollbackVersion = "1.9.0",
        )

        assertEquals("1.9.0", governance.effectiveVersion("2.0.0"))
    }
}
