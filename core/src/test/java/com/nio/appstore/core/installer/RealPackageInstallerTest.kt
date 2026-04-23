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
            writeBytes(TEST_APK_BYTES)
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
                targetVersion = TEST_TARGET_VERSION,
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
        assertEquals(TEST_TARGET_VERSION, (events.last() as InstallEvent.Success).installedVersion)
    }

    @Test
    fun `install 在系统安装会话不可用且无兜底安装器时返回明确失败`() = runBlocking {
        val workingDir = Files.createTempDirectory("real-package-installer-unsupported-test").toFile()
        val apkFile = File(workingDir, "demo.apk").apply {
            writeBytes(TEST_APK_BYTES)
        }
        val sessionStore = InstallSessionStore(File(workingDir, "install-sessions.json"))
        val installer = RealPackageInstaller(
            sessionAdapter = UnsupportedSessionAdapter(),
            sessionStore = sessionStore,
            fallbackInstaller = null,
        )
        val events = mutableListOf<InstallEvent>()

        installer.install(
            request = InstallRequest(
                appId = "demo.app",
                packageName = "com.demo.app",
                targetVersion = TEST_TARGET_VERSION,
                apkFile = apkFile,
            ),
            onEvent = { event -> events += event },
        )

        val failureEvent = events.filterIsInstance<InstallEvent.Failed>().single()

        assertEquals(InstallFailureCode.SESSION_NOT_SUPPORTED, failureEvent.code)
        assertEquals(InstallFailureCode.SESSION_NOT_SUPPORTED.displayText, failureEvent.message)
        assertTrue(events.none { it is InstallEvent.Success })
        assertTrue(sessionStore.readAll().isEmpty())
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

    private class UnsupportedSessionAdapter : PackageInstallerSessionAdapter {

        override fun createSession(request: InstallRequest): Int = error(UNEXPECTED_SESSION_CALL)

        override fun writeApkToSession(sessionId: Int, apkFile: File): Boolean = error(UNEXPECTED_SESSION_CALL)

        override suspend fun commitSession(
            sessionId: Int,
            onPendingUserAction: suspend (message: String, confirmationIntent: Intent) -> Unit,
        ): InstallCommitResult = error(UNEXPECTED_SESSION_CALL)

        override fun supportsRealSession(): Boolean = false
    }

    private companion object {
        /** 测试 APK 文件写入内容。 */
        val TEST_APK_BYTES = byteArrayOf(1, 2, 3, 4)

        /** 测试安装会话使用的固定 sessionId。 */
        const val TEST_SESSION_ID = 77

        /** 测试安装请求的目标版本。 */
        const val TEST_TARGET_VERSION = "1.0.1"

        /** 不应触达系统会话方法时抛出的测试错误。 */
        const val UNEXPECTED_SESSION_CALL = "系统会话不可用时不应继续调用会话方法"
    }
}
