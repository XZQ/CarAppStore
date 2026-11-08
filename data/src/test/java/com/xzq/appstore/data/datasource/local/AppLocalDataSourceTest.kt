package com.xzq.appstore.data.datasource.local

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.xzq.appstore.data.local.store.JsonBackedLocalStoreFacade
import com.xzq.appstore.data.model.DownloadTaskRecord
import com.xzq.appstore.domain.state.DownloadStatus
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
    fun `staged upgrade is consumed from both stores and stays consumed after reopening`() {
        val storeFile = File(context.filesDir, "structured_local_store.json")
        val persisted = AppLocalDataSource(context, JsonBackedLocalStoreFacade(storeFile))
        persisted.stageUpgradeVersion("app.a", "2.0")

        assertEquals("2.0", persisted.consumeStagedUpgradeVersion("app.a"))
        assertNull(persisted.peekStagedUpgradeVersion("app.a"))
        assertNull(persisted.consumeStagedUpgradeVersion("app.a"))
        assertNull(AppLocalDataSource(context, JsonBackedLocalStoreFacade(storeFile)).peekStagedUpgradeVersion("app.a"))
    }

    @Test
    fun `清理已完成任务时以 facade 中的真实记录为准`() {
        val storeFile = File(context.filesDir, "structured_local_store.json")
        val persisted = AppLocalDataSource(context, JsonBackedLocalStoreFacade(storeFile))
        persisted.saveDownloadTask(task("app.done", DownloadStatus.COMPLETED))
        persisted.saveDownloadTask(task("app.canceled", DownloadStatus.CANCELED))
        persisted.saveDownloadTask(task("app.running", DownloadStatus.RUNNING))

        // 重新打开后内存镜像为空，只有 facade 里还留着任务记录。
        val reopened = AppLocalDataSource(context, JsonBackedLocalStoreFacade(storeFile))
        assertEquals(2, reopened.clearCompletedDownloadTasks())

        assertNull(reopened.getDownloadTask("app.done"))
        assertNull(reopened.getDownloadTask("app.canceled"))
        assertEquals(DownloadStatus.RUNNING, reopened.getDownloadTask("app.running")?.status)
    }

    private fun task(appId: String, status: DownloadStatus) = DownloadTaskRecord(
        taskId = "task-$appId",
        appId = appId,
        status = status,
        progress = if (status == DownloadStatus.COMPLETED) 100 else 0,
        targetFilePath = File(context.filesDir, "$appId.apk").absolutePath,
        downloadedBytes = 0L,
        totalBytes = 0L,
        createdAt = 0L,
        updatedAt = 0L,
    )

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
