package com.nio.appstore.core.installer

import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RealPackageInstallerTest {

    @Test
    fun `install 会在系统确认后继续等待最终成功结果`() = runBlocking {
        val workingDir = Files.createTempDirectory("real-package-installer-test").toFile()
        val apkFile = File(workingDir, "demo.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val sessionStore = InstallSessionStore(File(workingDir, "install-sessions.json"))
        val installer = RealPackageInstaller(
            sessionAdapter = PendingThenSuccessSessionAdapter(),
            sessionStore = sessionStore,
            fallbackInstaller = null,
        )
        val events = mutableListOf<InstallEvent>()

        installer.install(
            request = InstallRequest(
                appId = "demo.app",
                packageName = "com.demo.app",
                targetVersion = "1.0.1",
                apkFile = apkFile,
            ),
            onEvent = { event -> events += event },
        )

        val pendingIndex = events.indexOfFirst { it is InstallEvent.PendingUserAction }
        val successIndex = events.indexOfFirst { it is InstallEvent.Success }
        val pendingEvent = events.filterIsInstance<InstallEvent.PendingUserAction>().single()

        assertTrue(pendingIndex >= 0)
        assertTrue(successIndex > pendingIndex)
        assertNotNull(pendingEvent.confirmationIntent)
        assertEquals(InstallSessionStatus.CALLBACK_SUCCESS, sessionStore.get(TEST_SESSION_ID)?.status)
        assertEquals(100, sessionStore.get(TEST_SESSION_ID)?.progress)
        assertEquals("1.0.1", (events.last() as InstallEvent.Success).installedVersion)
    }

    private class PendingThenSuccessSessionAdapter : PackageInstallerSessionAdapter {

        override fun createSession(request: InstallRequest): Int = TEST_SESSION_ID

        override fun writeApkToSession(sessionId: Int, apkFile: File): Boolean = true

        override suspend fun commitSession(
            sessionId: Int,
            onPendingUserAction: suspend (message: String, confirmationIntent: Intent) -> Unit,
        ): InstallCommitResult {
            onPendingUserAction(
                InstallerText.SESSION_PENDING_USER_ACTION,
                Intent("confirm.install"),
            )
            return InstallCommitResult(
                success = true,
                message = InstallerText.SESSION_COMMIT_SUCCESS,
                installedPackageName = "com.demo.app",
            )
        }

        override fun supportsRealSession(): Boolean = true
    }

    private companion object {
        /** 测试安装会话使用的固定 sessionId。 */
        const val TEST_SESSION_ID = 77
    }
}
