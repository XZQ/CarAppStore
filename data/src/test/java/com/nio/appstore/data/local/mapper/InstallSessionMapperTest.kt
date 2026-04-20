package com.nio.appstore.data.local.mapper

import com.nio.appstore.core.installer.InstallSessionRecord
import com.nio.appstore.data.local.entity.InstallSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallSessionMapperTest {

    // ── 辅助：构造一份所有字段均赋值的 InstallSessionRecord ──

    private fun buildRecord() = InstallSessionRecord(
        sessionId = 42,
        appId = "app.demo",
        packageName = "com.nio.demo",
        apkPath = "/data/tmp/app-demo.apk",
        targetVersion = "2.1.0",
        status = "PENDING_USER_ACTION",
        progress = 60,
        failureCode = "INSTALL_FAILED_VERIFICATION",
        failureMessage = "verification timeout",
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    private fun buildEntity() = InstallSessionEntity(
        sessionId = 42,
        appId = "app.demo",
        packageName = "com.nio.demo",
        apkPath = "/data/tmp/app-demo.apk",
        targetVersion = "2.1.0",
        status = "PENDING_USER_ACTION",
        progress = 60,
        failureCode = "INSTALL_FAILED_VERIFICATION",
        failureMessage = "verification timeout",
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    // ── 1. toEntity 正确映射所有字段 ──

    @Test
    fun `toEntity 正确映射所有字段`() {
        val record = buildRecord()
        val entity = InstallSessionMapper.toEntity(record)

        assertEquals(record.sessionId, entity.sessionId)
        assertEquals(record.appId, entity.appId)
        assertEquals(record.packageName, entity.packageName)
        assertEquals(record.apkPath, entity.apkPath)
        assertEquals(record.targetVersion, entity.targetVersion)
        assertEquals(record.status, entity.status)
        assertEquals(record.progress, entity.progress)
        assertEquals(record.failureCode, entity.failureCode)
        assertEquals(record.failureMessage, entity.failureMessage)
        assertEquals(record.createdAt, entity.createdAt)
        assertEquals(record.updatedAt, entity.updatedAt)
    }

    // ── 2. fromEntity 正确映射所有字段 ──

    @Test
    fun `fromEntity 正确映射所有字段`() {
        val entity = buildEntity()
        val record = InstallSessionMapper.fromEntity(entity)

        assertEquals(entity.sessionId, record.sessionId)
        assertEquals(entity.appId, record.appId)
        assertEquals(entity.packageName, record.packageName)
        assertEquals(entity.apkPath, record.apkPath)
        assertEquals(entity.targetVersion, record.targetVersion)
        assertEquals(entity.status, record.status)
        assertEquals(entity.progress, record.progress)
        assertEquals(entity.failureCode, record.failureCode)
        assertEquals(entity.failureMessage, record.failureMessage)
        assertEquals(entity.createdAt, record.createdAt)
        assertEquals(entity.updatedAt, record.updatedAt)
    }

    // ── 3. toEntity 和 fromEntity 往返一致 ──

    @Test
    fun `toEntity 和 fromEntity 往返一致`() {
        val original = buildRecord()
        val restored = InstallSessionMapper.fromEntity(InstallSessionMapper.toEntity(original))

        assertEquals(original, restored)
    }
}
