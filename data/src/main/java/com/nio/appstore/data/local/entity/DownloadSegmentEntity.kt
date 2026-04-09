package com.nio.appstore.data.local.entity

data class DownloadSegmentEntity(
    val segmentId: String,
    val taskId: String,
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val status: String,
    val tmpFilePath: String,
    val retryCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
