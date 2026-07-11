package com.xzq.appstore.core.installer

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
    ) {
        private val workingDir = Files.createTempDirectory("real-package-installer-test").toFile()
        private val apkFile = File(workingDir, "demo.apk").apply { writeBytes(TEST_APK_BYTES) }
        val sessionStore = InstallSessionStore(File(workingDir, "install-sessions.json"))
        private val installer = RealPackageInstaller(
            sessionAdapter = sessionAdapter,
            sessionStore = sessionStore,
            permissionGateway = permissionGateway,
            apkVerifier = FixedApkVerifier(verificationResult),
            installedPackageInspector = FixedInstalledPackageInspector(installedIdentity),
            verificationPolicy = STRICT_POLICY,
            fallbackInstaller = null,
        )

        suspend fun install(): List<InstallEvent> {
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
            ) { events += it }
            return events
        }
    }

    private class FixedApkVerifier(private val result: ApkVerificationResult) : ApkVerifier {
        override fun verify(apkFile: File, expected: ExpectedApkIdentity, policy: ApkVerificationPolicy): ApkVerificationResult = result
    }

    private class FixedInstalledPackageInspector(private val identity: ApkIdentity?) : InstalledPackageInspector {
        override fun getInstalledIdentity(packageName: String): ApkIdentity? = identity
    }

    private class RecordingSessionAdapter(private val installedPackageName: String? = TEST_PACKAGE_NAME) : PackageInstallerSessionAdapter {
        var createSessionCalls = 0

        override fun createSession(request: InstallRequest): Int {
            createSessionCalls += 1
            return TEST_SESSION_ID
        }

        override fun writeApkToSession(sessionId: Int, apkFile: File): Boolean = true

        override suspend fun commitSession(
            sessionId: Int,
            onPendingUserAction: suspend (message: String, confirmationIntent: Intent) -> Unit,
        ): InstallCommitResult {
            onPendingUserAction(InstallerText.SESSION_PENDING_USER_ACTION, Intent("confirm.install"))
            return InstallCommitResult(success = true, message = InstallerText.SESSION_COMMIT_SUCCESS, installedPackageName = installedPackageName)
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
