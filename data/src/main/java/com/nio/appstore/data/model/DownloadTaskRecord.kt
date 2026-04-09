package com.nio.appstore.data.model

import com.nio.appstore.domain.state.DownloadStatus

data class DownloadTaskRecord(
    val taskId: String,
    val appId: String,
    val status: DownloadStatus,
    val progress: Int,
    val targetFilePath: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val failureCode: String? = null,
    val failureMessage: String? = null,
    val retryCount: Int = 0,
    val downloadUrl: String? = null,
    val tempDirPath: String? = null,
    val eTag: String? = null,
    val lastModified: String? = null,
    val supportsRange: Boolean = false,
    val checksumType: String? = null,
    val checksumValue: String? = null,
    val segmentCount: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
)
