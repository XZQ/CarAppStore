package com.xzq.appstore.core.installer

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

class RealPackageInstallerTest {
    @Test
    fun `write failure abandons the created platform session`() = runBlocking {
        val adapter = RecordingSessionAdapter(writeOk = false)
        val fixture = Fixture(sessionAdapter = adapter)
        val events = fixture.install()
        assertEquals(InstallSessionStatus.FAILED_WRITE, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
        assertEquals(listOf(TEST_SESSION_ID), adapter.abandonedSessionIds)
        assertEquals(InstallFailureCode.SESSION_WRITE_FAILED, (events.last() as InstallEvent.Failed).code)
    }

    @Test
    fun `commit failure abandons the platform session`() = runBlocking {
        val adapter = RecordingSessionAdapter(commitSuccess = false)
        val fixture = Fixture(sessionAdapter = adapter)
        fixture.install()
        assertEquals(InstallSessionStatus.FAILED_COMMIT, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
        assertEquals(listOf(TEST_SESSION_ID), adapter.abandonedSessionIds)
    }

    @Test
    fun `cancel after session creation records interruption and abandons session`() = runBlocking {
        val adapter = RecordingSessionAdapter()
        val fixture = Fixture(sessionAdapter = adapter, installedIdentity = null)
        try {
            fixture.install { if (it is InstallEvent.SessionCreated) throw CancellationException("page closed") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(InstallSessionStatus.RECOVERED_INTERRUPTED, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
            assertEquals(InstallFailureCode.INSTALL_INTERRUPTED.name, fixture.sessionStore.get(TEST_SESSION_ID)?.failureCode)
            assertEquals(listOf(TEST_SESSION_ID), adapter.abandonedSessionIds)
        }
    }

    @Test
    fun `cancel during commit remains a recoverable interruption`() = runBlocking {
        val enteredCommit = CompletableDeferred<Unit>()
        val adapter = object : RecordingSessionAdapter() {
            override suspend fun commitSession(sessionId: Int, onPendingUserAction: suspend (String, Intent) -> Unit): InstallCommitResult {
                enteredCommit.complete(Unit)
                awaitCancellation()
            }
        }
        val fixture = Fixture(sessionAdapter = adapter, installedIdentity = null)
        val operation = launch { fixture.install() }
        withTimeout(2_000L) { enteredCommit.await() }
        operation.cancelAndJoin()
        assertEquals(InstallSessionStatus.RECOVERED_INTERRUPTED, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
        assertEquals(listOf(TEST_SESSION_ID), adapter.abandonedSessionIds)
    }

    @Test
    fun `cancel during commit publishes successful installed facts even before callback is returned`() = runBlocking {
        val enteredCommit = CompletableDeferred<Unit>()
        val installedIdentity = AtomicReference<ApkIdentity?>(null)
        val adapter = object : RecordingSessionAdapter() {
            override suspend fun commitSession(sessionId: Int, onPendingUserAction: suspend (String, Intent) -> Unit): InstallCommitResult {
                enteredCommit.complete(Unit)
                awaitCancellation()
            }
        }
        val fixture = Fixture(sessionAdapter = adapter, installedPackageInspector = object : InstalledPackageInspector {
            override fun getInstalledIdentity(packageName: String): ApkIdentity? = installedIdentity.get()
        })
        var successPublished = false
        val operation = launch { fixture.install { if (it is InstallEvent.Success) successPublished = true } }
        withTimeout(2_000L) { enteredCommit.await() }
        installedIdentity.set(INSTALLED_APK)
        operation.cancelAndJoin()

        assertTrue(operation.isCancelled)
        assertTrue(successPublished)
        assertEquals(InstallSessionStatus.CALLBACK_SUCCESS, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
        assertEquals(listOf(TEST_SESSION_ID), adapter.abandonedSessionIds)
    }

    @Test
    fun `cancel during commit cannot reconcile a target signed by another certificate`() = runBlocking {
        val adapter = object : RecordingSessionAdapter() {
            override suspend fun commitSession(sessionId: Int, onPendingUserAction: suspend (String, Intent) -> Unit): InstallCommitResult {
                throw CancellationException("callback canceled")
            }
        }
        val fixture = Fixture(sessionAdapter = adapter,
            installedIdentity = INSTALLED_APK.copy(signerCertificateSha256 = setOf("wrong-signer")))
        var successPublished = false
        try {
            fixture.install { if (it is InstallEvent.Success) successPublished = true }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(false, successPublished)
            assertEquals(InstallSessionStatus.RECOVERED_INTERRUPTED, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
            assertEquals(listOf(TEST_SESSION_ID), adapter.abandonedSessionIds)
        }
    }

    @Test
    fun `cancel with an already installed target still releases the redundant live session`() = runBlocking {
        val adapter = RecordingSessionAdapter()
        val fixture = Fixture(sessionAdapter = adapter)
        var successPublished = false
        try {
            fixture.install {
                if (it is InstallEvent.SessionCreated) throw CancellationException("page closed")
                if (it is InstallEvent.Success) successPublished = true
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(successPublished)
            assertEquals(InstallSessionStatus.CALLBACK_SUCCESS, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
            assertEquals(listOf(TEST_SESSION_ID), adapter.abandonedSessionIds)
        }
    }

    @Test
    fun `cancellation during final progress still publishes platform success`() = runBlocking {
        val adapter = RecordingSessionAdapter()
        val fixture = Fixture(sessionAdapter = adapter)
        var successPublished = false
        lateinit var operation: Job
        operation = launch(start = CoroutineStart.LAZY) {
            fixture.install { event ->
                if (event is InstallEvent.Progress && event.progress == 100) operation.cancel()
                if (event is InstallEvent.Success) successPublished = true
            }
        }
        // 取消发生在已确认成功后的进度回调；后续 Success 必须完成发布。
        operation.start()
        withTimeout(2_000L) { operation.join() }
        assertTrue(successPublished)
        assertEquals(InstallSessionStatus.CALLBACK_SUCCESS, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
        assertTrue(adapter.abandonedSessionIds.isEmpty())
    }

    @Test
    fun `install 会在系统确认后使用 PackageManager 实际版本收口成功`() = runBlocking {
        val fixture = Fixture()
        val events = fixture.install()
        val pendingIndex = events.indexOfFirst { it is InstallEvent.PendingUserAction }
        val successIndex = events.indexOfFirst { it is InstallEvent.Success }
        val pendingEvent = events.filterIsInstance<InstallEvent.PendingUserAction>().single()

        assertTrue(pendingIndex >= 0)
        assertTrue(successIndex > pendingIndex)
        assertNotNull(pendingEvent.confirmationIntent)
        assertEquals(InstallSessionStatus.CALLBACK_SUCCESS, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
        assertEquals(100, fixture.sessionStore.get(TEST_SESSION_ID)?.progress)
        assertEquals(TEST_INSTALLED_VERSION, (events.last() as InstallEvent.Success).installedVersion)
    }

    @Test
    fun `install APK 身份校验失败时不创建系统会话`() = runBlocking {
        val adapter = RecordingSessionAdapter()
        val fixture = Fixture(
            sessionAdapter = adapter,
            verificationResult = ApkVerificationResult.Rejected(InstallFailureCode.APK_SIGNER_MISMATCH),
        )

        val events = fixture.install()

        assertEquals(InstallFailureCode.APK_SIGNER_MISMATCH, (events.single() as InstallEvent.Failed).code)
        assertEquals(0, adapter.createSessionCalls)
        assertTrue(fixture.sessionStore.readAll().isEmpty())
    }

    @Test
    fun `install 平台回调包名不一致时标记事实校验失败`() = runBlocking {
        val fixture = Fixture(sessionAdapter = RecordingSessionAdapter(installedPackageName = "com.attacker.app"))

        val events = fixture.install()

        assertEquals(InstallFailureCode.INSTALLED_PACKAGE_MISMATCH, (events.last() as InstallEvent.Failed).code)
        assertEquals(InstallSessionStatus.FAILED_VERIFY_INSTALLED, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
    }

    @Test
    fun `install 成功回调后系统找不到目标包时失败`() = runBlocking {
        val fixture = Fixture(installedIdentity = null)

        val events = fixture.install()

        assertEquals(InstallFailureCode.INSTALLED_PACKAGE_NOT_FOUND, (events.last() as InstallEvent.Failed).code)
    }

    @Test
    fun `install 系统最终版本与已验证 APK 不一致时失败`() = runBlocking {
        val fixture = Fixture(installedIdentity = VERIFIED_APK.copy(versionCode = TEST_VERSION_CODE + 1))

        val events = fixture.install()

        assertEquals(InstallFailureCode.INSTALLED_VERSION_MISMATCH, (events.last() as InstallEvent.Failed).code)
    }

    @Test
    fun `install rejects successful callbacks when installed signer differs from the verified APK`() = runBlocking {
        val fixture = Fixture(installedIdentity = INSTALLED_APK.copy(signerCertificateSha256 = setOf("wrong-signer")))
        val events = fixture.install()
        assertEquals(InstallFailureCode.INSTALLED_SIGNER_MISMATCH, (events.last() as InstallEvent.Failed).code)
        assertEquals(InstallSessionStatus.FAILED_VERIFY_INSTALLED, fixture.sessionStore.get(TEST_SESSION_ID)?.status)
        assertTrue(events.none { it is InstallEvent.Success })
    }

    @Test
    fun `install 在系统安装会话不可用且无兜底安装器时返回明确失败`() = runBlocking {
        val fixture = Fixture(sessionAdapter = UnsupportedSessionAdapter())

        val events = fixture.install()
        val failureEvent = events.filterIsInstance<InstallEvent.Failed>().single()

        assertEquals(InstallFailureCode.SESSION_NOT_SUPPORTED, failureEvent.code)
        assertEquals(InstallFailureCode.SESSION_NOT_SUPPORTED.displayText, failureEvent.message)
        assertTrue(events.none { it is InstallEvent.Success })
        assertTrue(fixture.sessionStore.readAll().isEmpty())
    }

    @Test
    fun `install 在未知来源权限未授予时不创建系统会话`() = runBlocking {
        val adapter = RecordingSessionAdapter()
        val fixture = Fixture(sessionAdapter = adapter, permissionGateway = DeniedInstallPermissionGateway)

        val events = fixture.install()

        assertEquals(InstallFailureCode.PERMISSION_REQUIRED, (events.single() as InstallEvent.Failed).code)
        assertEquals(0, adapter.createSessionCalls)
    }

    private class Fixture(
        sessionAdapter: PackageInstallerSessionAdapter = RecordingSessionAdapter(),
        permissionGateway: InstallPermissionGateway = AllowedInstallPermissionGateway,
        verificationResult: ApkVerificationResult = ApkVerificationResult.Verified(VERIFIED_APK),
        installedIdentity: ApkIdentity? = INSTALLED_APK,
        installedPackageInspector: InstalledPackageInspector = FixedInstalledPackageInspector(installedIdentity),
    ) {
        private val workingDir = Files.createTempDirectory("real-package-installer-test").toFile()
        private val apkFile = File(workingDir, "demo.apk").apply { writeBytes(TEST_APK_BYTES) }
        val sessionStore = InstallSessionStore(File(workingDir, "install-sessions.json"))
        private val installer = RealPackageInstaller(
            sessionAdapter = sessionAdapter,
            sessionStore = sessionStore,
            permissionGateway = permissionGateway,
            apkVerifier = FixedApkVerifier(verificationResult),
            installedPackageInspector = installedPackageInspector,
            verificationPolicy = STRICT_POLICY,
            fallbackInstaller = null,
        )

        suspend fun install(onEvent: suspend (InstallEvent) -> Unit = {}): List<InstallEvent> {
            val events = mutableListOf<InstallEvent>()
            installer.install(
                InstallRequest(
                    appId = "demo.app",
                    packageName = TEST_PACKAGE_NAME,
                    targetVersion = TEST_TARGET_VERSION,
                    apkFile = apkFile,
                    targetVersionCode = TEST_VERSION_CODE,
                    signerCertificateSha256 = setOf(TEST_SIGNER),
                ),
            ) { events += it; onEvent(it) }
            return events
        }
    }

    private class FixedApkVerifier(private val result: ApkVerificationResult) : ApkVerifier {
        override fun verify(apkFile: File, expected: ExpectedApkIdentity, policy: ApkVerificationPolicy): ApkVerificationResult = result
    }

    private class FixedInstalledPackageInspector(private val identity: ApkIdentity?) : InstalledPackageInspector {
        override fun getInstalledIdentity(packageName: String): ApkIdentity? = identity
    }

    private open class RecordingSessionAdapter(private val installedPackageName: String? = TEST_PACKAGE_NAME,
        private val writeOk: Boolean = true, private val commitSuccess: Boolean = true) : PackageInstallerSessionAdapter {
        var createSessionCalls = 0
        val abandonedSessionIds = mutableListOf<Int>()

        override fun createSession(request: InstallRequest): Int {
            createSessionCalls += 1
            return TEST_SESSION_ID
        }

        override fun writeApkToSession(sessionId: Int, apkFile: File): Boolean = writeOk

        override suspend fun commitSession(
            sessionId: Int,
            onPendingUserAction: suspend (message: String, confirmationIntent: Intent) -> Unit,
        ): InstallCommitResult {
            onPendingUserAction(InstallerText.SESSION_PENDING_USER_ACTION, Intent("confirm.install"))
            return InstallCommitResult(success = commitSuccess, message = InstallerText.SESSION_COMMIT_SUCCESS, installedPackageName = installedPackageName)
        }

        override fun supportsRealSession(): Boolean = true

        override fun abandonSession(sessionId: Int): Boolean {
            abandonedSessionIds += sessionId
            return true
        }
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

    private object AllowedInstallPermissionGateway : InstallPermissionGateway {
        override fun canRequestInstalls(): Boolean = true
    }

    private object DeniedInstallPermissionGateway : InstallPermissionGateway {
        override fun canRequestInstalls(): Boolean = false
    }

    private companion object {
        val TEST_APK_BYTES = byteArrayOf(1, 2, 3, 4)
        const val TEST_SESSION_ID = 77
        const val TEST_PACKAGE_NAME = "com.demo.app"
        const val TEST_TARGET_VERSION = "1.0.1"
        const val TEST_INSTALLED_VERSION = "1.0.2"
        const val TEST_VERSION_CODE = 101L
        const val TEST_SIGNER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val UNEXPECTED_SESSION_CALL = "系统会话不可用时不应继续调用会话方法"
        val VERIFIED_APK = ApkIdentity(TEST_PACKAGE_NAME, TEST_VERSION_CODE, TEST_TARGET_VERSION, setOf(TEST_SIGNER))
        val INSTALLED_APK = VERIFIED_APK.copy(versionName = TEST_INSTALLED_VERSION)
        val STRICT_POLICY = ApkVerificationPolicy(requireVersionCode = true, requireSignerCertificate = true)
    }
}
