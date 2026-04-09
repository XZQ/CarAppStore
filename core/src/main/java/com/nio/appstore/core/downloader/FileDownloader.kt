package com.nio.appstore.core.downloader

import java.io.File

interface FileDownloader {
    suspend fun download(
        request: DownloadRequest,
        onEvent: suspend (DownloadEvent) -> Unit,
    )
}

data class DownloadRequest(
    /** 稳定的下载任务标识。 */
    val taskId: String,
    /** 与任务关联的应用标识。 */
    val appId: String,
    /** 最终解析得到的下载地址。 */
    val url: String,
    /** 磁盘上的最终目标文件。 */
    val targetFile: File,
    /** 当前尝试开始前已下载的字节数。 */
    val downloadedBytes: Long,
    /** 已知时的期望总字节数。 */
    val totalBytes: Long,
    /** 当前任务已经消耗的重试次数。 */
    val attempt: Int = 0,
    /** 用于探测远端文件变化的缓存 ETag。 */
    val eTag: String? = null,
    /** 用于探测远端文件变化的缓存 Last-Modified。 */
    val lastModified: String? = null,
    /** 上一次探测结果是否表明支持 Range 请求。 */
    val supportsRange: Boolean = false,
    /** 文件校验时使用的可选算法。 */
    val checksumType: String? = null,
    /** 文件校验时使用的可选期望值。 */
    val checksumValue: String? = null,
    /** 当前请求优先采用的下载源策略。 */
    val sourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
)

enum class DownloadFailureCode(val displayText: String, val retryable: Boolean) {
    NETWORK_TIMEOUT(DownloaderText.FAILURE_NETWORK_TIMEOUT, true),
    NETWORK_INTERRUPTED(DownloaderText.FAILURE_NETWORK_INTERRUPTED, true),
    HTTP_4XX(DownloaderText.FAILURE_HTTP_4XX, false),
    HTTP_5XX(DownloaderText.FAILURE_HTTP_5XX, true),
    RANGE_NOT_SUPPORTED(DownloaderText.FAILURE_RANGE_NOT_SUPPORTED, false),
    REMOTE_FILE_CHANGED(DownloaderText.FAILURE_REMOTE_FILE_CHANGED, false),
    STORAGE_IO(DownloaderText.FAILURE_STORAGE_IO, true),
    FILE_MISSING(DownloaderText.FAILURE_FILE_MISSING, true),
    FILE_INCOMPLETE(DownloaderText.FAILURE_FILE_INCOMPLETE, true),
    CHECKSUM_MISMATCH(DownloaderText.FAILURE_CHECKSUM_MISMATCH, false),
    MERGE_FAILED(DownloaderText.FAILURE_MERGE_FAILED, false),
    USER_CANCELED(DownloaderText.FAILURE_USER_CANCELED, true),
    UNKNOWN(DownloaderText.FAILURE_UNKNOWN, true),
}

sealed class DownloadEvent {
    object Waiting : DownloadEvent()

    data class MetaReady(
        /** 探测远端后拿到的元数据。 */
        val meta: DownloadRemoteMeta,
    ) : DownloadEvent()

    data class Running(
        /** 所有分片聚合后的已下载字节数。 */
        val downloadedBytes: Long,
        /** 当前下载任务的期望总字节数。 */
        val totalBytes: Long,
        /** 当前估算出的瞬时下载速度。 */
        val speedBytesPerSec: Long,
    ) : DownloadEvent()

    data class Completed(
        /** 最终合并完成的文件。 */
        val file: File,
        /** 完成时确认的总字节数。 */
        val totalBytes: Long,
    ) : DownloadEvent()

    data class Failed(
        /** 归一化后的失败码。 */
        val code: DownloadFailureCode,
        /** 展示给用户的失败详情。 */
        val message: String,
        /** 当前失败是否适合继续重试。 */
        val retryable: Boolean = code.retryable,
    ) : DownloadEvent()
}
