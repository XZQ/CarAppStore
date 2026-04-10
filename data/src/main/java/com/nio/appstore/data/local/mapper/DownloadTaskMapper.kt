package com.nio.appstore.data.local.mapper

import com.nio.appstore.data.local.entity.DownloadSegmentEntity
import com.nio.appstore.data.local.entity.DownloadTaskEntity
import com.nio.appstore.data.model.DownloadSegmentRecord
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.domain.state.DownloadStatus

object DownloadTaskMapper {
    /** 把下载任务业务模型转换为本地实体。 */
    fun toEntity(record: DownloadTaskRecord): DownloadTaskEntity = DownloadTaskEntity(
        taskId = record.taskId,
        appId = record.appId,
        status = record.status.name,
        progress = record.progress,
        targetFilePath = record.targetFilePath,
        downloadedBytes = record.downloadedBytes,
        totalBytes = record.totalBytes,
        speedBytesPerSec = record.speedBytesPerSec,
        failureCode = record.failureCode,
        failureMessage = record.failureMessage,
        retryCount = record.retryCount,
        downloadUrl = record.downloadUrl,
        tempDirPath = record.tempDirPath,
        eTag = record.eTag,
        lastModified = record.lastModified,
        supportsRange = record.supportsRange,
        checksumType = record.checksumType,
        checksumValue = record.checksumValue,
        segmentCount = record.segmentCount,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
    )

    /** 把本地下载任务实体转换为业务模型。 */
    fun fromEntity(entity: DownloadTaskEntity): DownloadTaskRecord = DownloadTaskRecord(
        taskId = entity.taskId,
        appId = entity.appId,
        status = runCatching { DownloadStatus.valueOf(entity.status) }.getOrElse { DownloadStatus.IDLE },
        progress = entity.progress,
        targetFilePath = entity.targetFilePath,
        downloadedBytes = entity.downloadedBytes,
        totalBytes = entity.totalBytes,
        speedBytesPerSec = entity.speedBytesPerSec,
        failureCode = entity.failureCode,
        failureMessage = entity.failureMessage,
        retryCount = entity.retryCount,
        downloadUrl = entity.downloadUrl,
        tempDirPath = entity.tempDirPath,
        eTag = entity.eTag,
        lastModified = entity.lastModified,
        supportsRange = entity.supportsRange,
        checksumType = entity.checksumType,
        checksumValue = entity.checksumValue,
        segmentCount = entity.segmentCount,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /** 把下载分片业务模型转换为本地实体。 */
    fun toSegmentEntity(record: DownloadSegmentRecord): DownloadSegmentEntity = DownloadSegmentEntity(
        segmentId = record.segmentId,
        taskId = record.taskId,
        index = record.index,
        startByte = record.startByte,
        endByte = record.endByte,
        downloadedBytes = record.downloadedBytes,
        status = record.status,
        tmpFilePath = record.tmpFilePath,
        retryCount = record.retryCount,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
    )

    /** 把本地下载分片实体转换为业务模型。 */
    fun fromSegmentEntity(entity: DownloadSegmentEntity): DownloadSegmentRecord = DownloadSegmentRecord(
        segmentId = entity.segmentId,
        taskId = entity.taskId,
        index = entity.index,
        startByte = entity.startByte,
        endByte = entity.endByte,
        downloadedBytes = entity.downloadedBytes,
        status = entity.status,
        tmpFilePath = entity.tmpFilePath,
        retryCount = entity.retryCount,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )
}
