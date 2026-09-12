package com.xzq.appstore.data.datasource.system

import android.content.Context
import android.os.storage.StorageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowStorageManager
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [AndroidStorageInfoProviderTest.StorageShadow::class])
class AndroidStorageInfoProviderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var provider: AndroidStorageInfoProvider

    @Before
    fun setUp() {
        StorageShadow.allocatableBytes = Long.MAX_VALUE
        StorageShadow.failure = null
        StorageShadow.queriedDirectory = null
        provider = AndroidStorageInfoProvider(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `reclaimable cache is not counted as writable and the target volume is queried`() {
        val target = directoryWithSpace(32L * 1024 * 1024)
        StorageShadow.allocatableBytes = 128L * 1024 * 1024

        assertEquals(32L * 1024 * 1024, provider.usableSpaceBytes(target))
        assertEquals(target, StorageShadow.queriedDirectory)
    }

    @Test
    fun `platform allocation limit can reduce the physical space estimate`() {
        StorageShadow.allocatableBytes = 4L * 1024 * 1024

        assertEquals(4L * 1024 * 1024, provider.usableSpaceBytes(directoryWithSpace(32L * 1024 * 1024)))
    }

    @Test
    fun `unavailable volume information falls back to physical writable space`() {
        StorageShadow.failure = IOException("Volume information is unavailable")

        assertEquals(1024L, provider.usableSpaceBytes(directoryWithSpace(1024L)))
    }

    @Test
    fun `restricted storage service falls back to physical writable space`() {
        StorageShadow.failure = SecurityException("Storage service is restricted")

        assertEquals(2048L, provider.usableSpaceBytes(directoryWithSpace(2048L)))
    }

    @Test
    fun `streaming space checks use the filesystem without querying the storage service`() {
        StorageShadow.failure = IllegalStateException("Streaming must not query allocation information")

        assertEquals(4096L, provider.writableSpaceBytes(directoryWithSpace(4096L)))
        assertNull(StorageShadow.queriedDirectory)
    }

    @Test
    fun `a file cannot be used as a download directory`() {
        assertEquals(0L, provider.usableSpaceBytes(temporaryFolder.newFile("not-a-directory")))
        assertNull(StorageShadow.queriedDirectory)
    }

    private fun directoryWithSpace(bytes: Long): File = object : File(temporaryFolder.newFolder().path) {
        override fun getUsableSpace(): Long = bytes
    }

    @Implements(StorageManager::class)
    class StorageShadow : ShadowStorageManager() {
        @Implementation(minSdk = 26)
        fun getUuidForPath(directory: File): UUID {
            queriedDirectory = directory
            failure?.let { throw it }
            return StorageManager.UUID_DEFAULT
        }

        @Implementation(minSdk = 26)
        fun getAllocatableBytes(uuid: UUID): Long {
            check(uuid == StorageManager.UUID_DEFAULT)
            return allocatableBytes
        }

        companion object {
            var allocatableBytes = Long.MAX_VALUE
            var failure: Exception? = null
            var queriedDirectory: File? = null
        }
    }
}
