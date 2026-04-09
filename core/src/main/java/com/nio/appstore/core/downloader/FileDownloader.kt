package com.nio.appstore.core.downloader

import java.io.File

interface FileDownloader {
    suspend fun download(
        request: DownloadRequest,
        onEvent: suspend (DownloadEvent) -> Unit,
    )
}

data class DownloadRequest(
    val taskId: String,
    val appId: String,
    val url: String,
    val targetFile: File,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val attempt: Int = 0,
    val eTag: String? = null,
    val lastModified: String? = null,
    val supportsRange: Boolean = false,
    val checksumType: String? = null,
    val checksumValue: String? = null,
    val sourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
)

enum class DownloadFailureCode(val displayText: String, val retryable: Boolean) {
    NETWORK_TIMEOUT("下载超时", true),
    NETWORK_INTERRUPTED("网络中断", true),
    HTTP_4XX("下载地址无效", false),
    HTTP_5XX("服务端异常", true),
    RANGE_NOT_SUPPORTED("服务端不支持断点续传", false),
    REMOTE_FILE_CHANGED("远端文件已变化", false),
    STORAGE_IO("存储写入失败", true),
    FILE_MISSING("下载文件丢失", true),
    FILE_INCOMPLETE("文件未完整下载", true),
    CHECKSUM_MISMATCH("文件校验失败", false),
    MERGE_FAILED("分片合并失败", false),
    USER_CANCELED("用户取消下载", true),
    UNKNOWN("未知下载错误", true),
}

sealed class DownloadEvent {
    object Waiting : DownloadEvent()

    data class MetaReady(
        val meta: DownloadRemoteMeta,
    ) : DownloadEvent()

    data class Running(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
    ) : DownloadEvent()

    data class Completed(
        val file: File,
        val totalBytes: Long,
    ) : DownloadEvent()

    data class Failed(
        val code: DownloadFailureCode,
        val message: String,
        val retryable: Boolean = code.retryable,
    ) : DownloadEvent()
}
