package com.xzq.appstore.core.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * InstallSessionReconcilerTest 验证进程死亡后本地记录与平台安装事实的对账规则。
 */
class InstallSessionReconcilerTest {
    @Test
    fun `已安装目标版本会补记成功且不放弃平台会话`() {
        val store = createStoreWithRecoverableSession()
        val gateway = RecordingOwnedSessionGateway(setOf(TEST_SESSION_ID))
        val inspector = FixedInstalledPackageInspector(
            ApkIdentity(TEST_PACKAGE_NAME, 10L, TEST_TARGET_VERSION, setOf("signer")),
        )

        val result = InstallSessionReconciler(store, gateway, inspector).reconcile()

        assertEquals(InstallSessionStatus.CALLBACK_SUCCESS, store.get(TEST_SESSION_ID)?.status)
        assertEquals(1, result.completedSessionCount)
        assertTrue(gateway.abandonedSessionIds.isEmpty())
    }

    @Test
    fun `遗留平台会话会被放弃并转为可重试中断态`() {
        val store = createStoreWithRecoverableSession()
        val gateway = RecordingOwnedSessionGateway(setOf(TEST_SESSION_ID))

        val result = InstallSessionReconciler(store, gateway, FixedInstalledPackageInspector(null)).reconcile()

        assertEquals(InstallSessionStatus.RECOVERED_INTERRUPTED, store.get(TEST_SESSION_ID)?.status)
        assertEquals(InstallFailureCode.INSTALL_INTERRUPTED.name, store.get(TEST_SESSION_ID)?.failureCode)
        assertEquals(setOf(TEST_SESSION_ID), gateway.abandonedSessionIds)
        assertEquals(1, result.interruptedSessionCount)
        assertEquals(1, result.abandonedPlatformSessionCount)
    }

    @Test
    fun `平台会话已消失时仍转为可重试中断态`() {
        val store = createStoreWithRecoverableSession()
        val gateway = RecordingOwnedSessionGateway(emptySet())

        val result = InstallSessionReconciler(store, gateway, FixedInstalledPackageInspector(null)).reconcile()

        assertEquals(InstallSessionStatus.RECOVERED_INTERRUPTED, store.get(TEST_SESSION_ID)?.status)
        assertEquals(1, result.interruptedSessionCount)
        assertEquals(0, result.abandonedPlatformSessionCount)
    }

    private fun createStoreWithRecoverableSession(): InstallSessionStore {
        val store = InstallSessionStore(Files.createTempDirectory("install-session-reconciler-test").resolve("sessions.json").toFile())
        store.save(
            InstallSessionRecord(
                sessionId = TEST_SESSION_ID,
                appId = "test.app",
                packageName = TEST_PACKAGE_NAME,
                apkPath = File("test.apk").absolutePath,
                targetVersion = TEST_TARGET_VERSION,
                status = InstallSessionStatus.PENDING_USER_ACTION,
                progress = 90,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )
        return store
    }

    private class RecordingOwnedSessionGateway(private val sessionIds: Set<Int>) : OwnedInstallSessionGateway {
        val abandonedSessionIds = linkedSetOf<Int>()

        override fun ownedSessionIds(): Set<Int> = sessionIds

        override fun abandonSession(sessionId: Int): Boolean {
            abandonedSessionIds += sessionId
            return true
        }
    }

    private class FixedInstalledPackageInspector(private val identity: ApkIdentity?) : InstalledPackageInspector {
        override fun getInstalledIdentity(packageName: String): ApkIdentity? = identity
    }

    private companion object {
        const val TEST_SESSION_ID = 42
        const val TEST_PACKAGE_NAME = "com.example.test"
        const val TEST_TARGET_VERSION = "2.0.0"
    }
}
