package com.nio.appstore.core.installer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class InstallSessionStoreTest {

    @Test
    fun `get 会迁移旧数组格式并完成回写`() {
        val storeFile = createTempFile("install-sessions.json")
        storeFile.writeText(
            """
            [
              {
                "sessionId": 7,
                "appId": "app.demo",
                "packageName": "com.demo.app",
                "apkPath": "/tmp/demo.apk",
                "targetVersion": "1.2.0",
                "status": "WRITTEN",
                "progress": 55,
                "createdAt": 100,
                "updatedAt": 200
              }
            ]
            """.trimIndent()
        )
        val store = InstallSessionStore(storeFile)

        val record = store.get(7)
        val persisted = JSONObject(storeFile.readText())

        assertNotNull(record)
        assertEquals(InstallSessionStatus.WRITTEN, record?.status)
        assertEquals(1, persisted.optInt("schemaVersion"))
        assertEquals(1, persisted.getJSONArray("sessions").length())
    }

    @Test
    fun `clearFailedSessions 只移除失败和可重试会话`() {
        val storeFile = createTempFile("install-sessions-clear.json")
        val store = InstallSessionStore(storeFile)
        store.save(
            InstallSessionRecord(
                sessionId = 1,
                appId = "app.failed",
                packageName = "pkg.failed",
                apkPath = "/tmp/failed.apk",
                targetVersion = "1.0.0",
                status = InstallSessionStatus.FAILED_COMMIT,
                createdAt = 1,
                updatedAt = 2,
            )
        )
        store.save(
            InstallSessionRecord(
                sessionId = 2,
                appId = "app.recovered",
                packageName = "pkg.recovered",
                apkPath = "/tmp/recovered.apk",
                targetVersion = "1.0.0",
                status = InstallSessionStatus.RECOVERED_INTERRUPTED,
                createdAt = 3,
                updatedAt = 4,
            )
        )
        store.save(
            InstallSessionRecord(
                sessionId = 3,
                appId = "app.active",
                packageName = "pkg.active",
                apkPath = "/tmp/active.apk",
                targetVersion = "1.0.0",
                status = InstallSessionStatus.PENDING_USER_ACTION,
                createdAt = 5,
                updatedAt = 6,
            )
        )

        val removed = store.clearFailedSessions()

        assertEquals(2, removed)
        assertNull(store.get(1))
        assertNull(store.get(2))
        assertEquals(InstallSessionStatus.PENDING_USER_ACTION, store.get(3)?.status)
    }

    private fun createTempFile(fileName: String): File {
        return Files.createTempDirectory("install-session-store-test").resolve(fileName).toFile()
    }
}
