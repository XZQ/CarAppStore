package com.nio.appstore.data.local.mapper

import com.nio.appstore.data.local.entity.DownloadSegmentEntity
import com.nio.appstore.data.local.entity.DownloadTaskEntity
import com.nio.appstore.data.model.DownloadSegmentRecord
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.domain.state.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadTaskMapperTest {

    // ── 辅助：构造一份所有字段均赋值的 DownloadTaskRecord ──

    private fun buildRecord() = DownloadTaskRecord(
        taskId = "task-001",
        appId = "app.demo",
        status = DownloadStatus.RUNNING,
        progress = 42,
        targetFilePath = "/data/tmp/app-demo.apk",
        downloadedBytes = 1024L,
        totalBytes = 2048L,
        speedBytesPerSec = 512L,
        failureCode = "NET_ERR",
        failureMessage = "connection timeout",
        retryCount = 3,
        downloadUrl = "https://example.com/app.apk",
        tempDirPath = "/data/tmp/segments",
        eTag = "\"abc123\"",
        lastModified = "Wed, 01 Jan 2025 00:00:00 GMT",
        supportsRange = true,
        checksumType = "SHA-256",
        checksumValue = "deadbeef",
        segmentCount = 4,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    private fun buildEntity() = DownloadTaskEntity(
        taskId = "task-001",
        appId = "app.demo",
        status = "RUNNING",
        progress = 42,
        targetFilePath = "/data/tmp/app-demo.apk",
        downloadedBytes = 1024L,
        totalBytes = 2048L,
        speedBytesPerSec = 512L,
        failureCode = "NET_ERR",
        failureMessage = "connection timeout",
        retryCount = 3,
        downloadUrl = "https://example.com/app.apk",
        tempDirPath = "/data/tmp/segments",
        eTag = "\"abc123\"",
        lastModified = "Wed, 01 Jan 2025 00:00:00 GMT",
        supportsRange = true,
        checksumType = "SHA-256",
        checksumValue = "deadbeef",
        segmentCount = 4,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    // ── 1. toEntity 正确映射所有字段 ──

    @Test
    fun `toEntity 正确映射所有字段`() {
        val record = buildRecord()
        val entity = DownloadTaskMapper.toEntity(record)

        assertEquals(record.taskId, entity.taskId)
        assertEquals(record.appId, entity.appId)
        assertEquals(record.status.name, entity.status)
        assertEquals(record.progress, entity.progress)
        assertEquals(record.targetFilePath, entity.targetFilePath)
        assertEquals(record.downloadedBytes, entity.downloadedBytes)
        assertEquals(record.totalBytes, entity.totalBytes)
        assertEquals(record.speedBytesPerSec, entity.speedBytesPerSec)
        assertEquals(record.failureCode, entity.failureCode)
        assertEquals(record.failureMessage, entity.failureMessage)
        assertEquals(record.retryCount, entity.retryCount)
        assertEquals(record.downloadUrl, entity.downloadUrl)
        assertEquals(record.tempDirPath, entity.tempDirPath)
        assertEquals(record.eTag, entity.eTag)
        assertEquals(record.lastModified, entity.lastModified)
        assertEquals(record.supportsRange, entity.supportsRange)
        assertEquals(record.checksumType, entity.checksumType)
        assertEquals(record.checksumValue, entity.checksumValue)
        assertEquals(record.segmentCount, entity.segmentCount)
        assertEquals(record.createdAt, entity.createdAt)
        assertEquals(record.updatedAt, entity.updatedAt)
    }

    // ── 2. fromEntity 正确映射所有字段 ──

    @Test
    fun `fromEntity 正确映射所有字段`() {
        val entity = buildEntity()
        val record = DownloadTaskMapper.fromEntity(entity)

        assertEquals(entity.taskId, record.taskId)
        assertEquals(entity.appId, record.appId)
        assertEquals(DownloadStatus.RUNNING, record.status)
        assertEquals(entity.progress, record.progress)
        assertEquals(entity.targetFilePath, record.targetFilePath)
        assertEquals(entity.downloadedBytes, record.downloadedBytes)
        assertEquals(entity.totalBytes, record.totalBytes)
        assertEquals(entity.speedBytesPerSec, record.speedBytesPerSec)
        assertEquals(entity.failureCode, record.failureCode)
        assertEquals(entity.failureMessage, record.failureMessage)
        assertEquals(entity.retryCount, record.retryCount)
        assertEquals(entity.downloadUrl, record.downloadUrl)
        assertEquals(entity.tempDirPath, record.tempDirPath)
        assertEquals(entity.eTag, record.eTag)
        assertEquals(entity.lastModified, record.lastModified)
        assertEquals(entity.supportsRange, record.supportsRange)
        assertEquals(entity.checksumType, record.checksumType)
        assertEquals(entity.checksumValue, record.checksumValue)
        assertEquals(entity.segmentCount, record.segmentCount)
        assertEquals(entity.createdAt, record.createdAt)
        assertEquals(entity.updatedAt, record.updatedAt)
    }

    // ── 3. fromEntity 状态字符串非法时回退到 IDLE ──

    @Test
    fun `fromEntity 状态字符串非法时回退到 IDLE`() {
        val entity = buildEntity().copy(status = "INVALID_STATUS")
        val record = DownloadTaskMapper.fromEntity(entity)

        assertEquals(DownloadStatus.IDLE, record.status)
    }

    // ── 4. toEntity 和 fromEntity 往返一致 ──

    @Test
    fun `toEntity 和 fromEntity 往返一致`() {
        val original = buildRecord()
        val restored = DownloadTaskMapper.fromEntity(DownloadTaskMapper.toEntity(original))

        assertEquals(original, restored)
    }

    // ── 辅助：构造分片记录 ──

    private fun buildSegmentRecord() = DownloadSegmentRecord(
        segmentId = "seg-001",
        taskId = "task-001",
        index = 2,
        startByte = 1024L,
        endByte = 2047L,
        downloadedBytes = 512L,
        status = "RUNNING",
        tmpFilePath = "/data/tmp/seg-001.tmp",
        retryCount = 1,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    private fun buildSegmentEntity() = DownloadSegmentEntity(
        segmentId = "seg-001",
        taskId = "task-001",
        index = 2,
        startByte = 1024L,
        endByte = 2047L,
        downloadedBytes = 512L,
        status = "RUNNING",
        tmpFilePath = "/data/tmp/seg-001.tmp",
        retryCount = 1,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    // ── 5. toSegmentEntity 正确映射所有字段 ──

    @Test
    fun `toSegmentEntity 正确映射所有字段`() {
        val record = buildSegmentRecord()
        val entity = DownloadTaskMapper.toSegmentEntity(record)

        assertEquals(record.segmentId, entity.segmentId)
        assertEquals(record.taskId, entity.taskId)
        assertEquals(record.index, entity.index)
        assertEquals(record.startByte, entity.startByte)
        assertEquals(record.endByte, entity.endByte)
        assertEquals(record.downloadedBytes, entity.downloadedBytes)
        assertEquals(record.status, entity.status)
        assertEquals(record.tmpFilePath, entity.tmpFilePath)
        assertEquals(record.retryCount, entity.retryCount)
        assertEquals(record.createdAt, entity.createdAt)
        assertEquals(record.updatedAt, entity.updatedAt)
    }

    // ── 6. fromSegmentEntity 正确映射所有字段 ──

    @Test
    fun `fromSegmentEntity 正确映射所有字段`() {
        val entity = buildSegmentEntity()
        val record = DownloadTaskMapper.fromSegmentEntity(entity)

        assertEquals(entity.segmentId, record.segmentId)
        assertEquals(entity.taskId, record.taskId)
        assertEquals(entity.index, record.index)
        assertEquals(entity.startByte, record.startByte)
        assertEquals(entity.endByte, record.endByte)
        assertEquals(entity.downloadedBytes, record.downloadedBytes)
        assertEquals(entity.status, record.status)
        assertEquals(entity.tmpFilePath, record.tmpFilePath)
        assertEquals(entity.retryCount, record.retryCount)
        assertEquals(entity.createdAt, record.createdAt)
        assertEquals(entity.updatedAt, record.updatedAt)
    }
}
