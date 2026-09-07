package com.xzq.appstore.data.datasource.local

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppLocalDataSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var local: AppLocalDataSource

    @Before
    fun setUp() {
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir(): File = temporaryFolder.root
            override fun getApplicationContext(): Context = this
        }
        local = AppLocalDataSource(context)
    }

    @Test
    fun `distinct ids never alias and remain inside download directory`() {
        val ids = listOf("app.a", "app_a", "App.a", "app-a", "../app_a", "a/b", "a_b")
        val targets = ids.map(local::getOrCreateDownloadFile)
        assertEquals(ids.size, targets.map { it.canonicalPath.lowercase() }.toSet().size)
        assertTrue(targets.all { it.canonicalFile.parentFile == File(context.filesDir, "downloads").canonicalFile })
        assertEquals(targets, ids.map { AppLocalDataSource(context).getOrCreateDownloadFile(it) })
    }

    @Test
    fun `clearing one new artifact preserves another`() {
        val first = local.getOrCreateDownloadFile("app.a").apply { writeText("first") }
        val second = local.getOrCreateDownloadFile("app_a").apply { writeText("second") }
        local.saveDownloadedApk("app.a", first.path)
        local.saveDownloadedApk("app_a", second.path)
        local.clearDownloadedApk("app.a")
        assertFalse(first.exists())
        assertEquals("second", second.readText())
        assertEquals(second.path, local.getDownloadedApk("app_a"))
    }

    @Test
    fun `legacy shared references survive recreation without overwriting or deleting shared bytes`() {
        val legacy = File(context.filesDir, "downloads/app_a.apk").apply { writeText("legacy") }
        local.saveDownloadedApk("app.a", legacy.path)
        local.saveDownloadedApk("app_a", legacy.path)
        val restored = AppLocalDataSource(context)
        assertEquals(legacy.path, restored.getDownloadedApk("app.a"))
        val newTarget = restored.getOrCreateDownloadFile("app.a")
        assertNotEquals(legacy.path, newTarget.path)
        newTarget.writeText("new download")
        restored.clearDownloadedApk("app.a")
        assertEquals("legacy", legacy.readText())
        assertEquals(legacy.path, AppLocalDataSource(context).getDownloadedApk("app_a"))
        assertNull(AppLocalDataSource(context).getDownloadedApk("app.a"))
    }
}
