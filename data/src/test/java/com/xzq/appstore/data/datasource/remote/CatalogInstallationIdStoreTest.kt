package com.xzq.appstore.data.datasource.remote

import java.util.UUID
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CatalogInstallationIdStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `recreation keeps cohort while fresh installation creates a new one`() {
        val firstInstall = temporaryFolder.newFolder("first")
        val id = CatalogInstallationIdStore(firstInstall).getOrCreate()
        assertEquals(id, UUID.fromString(id).toString())
        assertEquals(id, CatalogInstallationIdStore(firstInstall).getOrCreate())
        assertNotEquals(id, CatalogInstallationIdStore(temporaryFolder.newFolder("second")).getOrCreate())
    }

    @Test
    fun `interrupted atomic write recovers previous cohort`() {
        val directory = temporaryFolder.newFolder("interrupted")
        val expected = UUID.randomUUID().toString()
        File(directory, "catalog_installation_id.bak").writeText(expected)
        assertEquals(expected, CatalogInstallationIdStore(directory).getOrCreate())
    }
}
