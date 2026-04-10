package com.nio.appstore.core.downloader

/**
 * DownloaderText 收敛下载器内部使用的失败文案、状态码和提示文案。
 */
object DownloaderText {
    /** 网络超时失败文案。 */
    const val FAILURE_NETWORK_TIMEOUT = "下载超时"
    const val FAILURE_NETWORK_INTERRUPTED = "网络中断"
    const val FAILURE_HTTP_4XX = "下载地址无效"
    const val FAILURE_HTTP_5XX = "服务端异常"
    const val FAILURE_RANGE_NOT_SUPPORTED = "服务端不支持断点续传"
    const val FAILURE_REMOTE_FILE_CHANGED = "远端文件已变化"
    const val FAILURE_STORAGE_IO = "存储写入失败"
    const val FAILURE_FILE_MISSING = "下载文件丢失"
    const val FAILURE_FILE_INCOMPLETE = "文件未完整下载"
    const val FAILURE_CHECKSUM_MISMATCH = "文件校验失败"
    const val FAILURE_MERGE_FAILED = "分片合并失败"
    const val FAILURE_USER_CANCELED = "用户取消下载"
    const val FAILURE_UNKNOWN = "未知下载错误"

    const val STATUS_WAITING = "WAITING"
    const val STATUS_RESUMING = "RESUMING"
    const val STATUS_RUNNING = "RUNNING"
    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_FAILED_INCOMPLETE = "FAILED_INCOMPLETE"
    const val STATUS_FAILED_TIMEOUT = "FAILED_TIMEOUT"
    const val STATUS_FAILED_IO = "FAILED_IO"

    const val DIRECT_HTTP_DISABLED = "当前环境禁用 DIRECT_HTTP，回退模拟下载"
    const val DIRECT_HTTP_ENABLED = "当前环境允许 DIRECT_HTTP"
    const val UNSUPPORTED_URL_PROTOCOL = "下载 URL 协议不受支持，回退模拟下载"
    const val MOCK_ENABLED = "当前环境允许 MOCK 下载源"
    const val MOCK_DISABLED = "当前环境禁用 MOCK，回退模拟下载"
    const val FALLBACK_SIMULATED = "当前策略指定回退模拟下载"

    const val REMOTE_FILE_CHANGED_REDOWNLOAD = "远端文件已变化，需重新下载"
    const val UNKNOWN_DOWNLOAD_FAILURE = "下载失败"
    const val SEGMENT_DOWNLOAD_FAILED = "分段下载失败"
    const val RANGE_RESPONSE_INVALID = "断点续传未返回 206 Partial Content"
    const val SEGMENT_FILE_INCOMPLETE = "分段文件未完整下载"
    const val FILE_NOT_EXISTS = "下载文件不存在"

    private const val FILE_LENGTH_MISMATCH_FORMAT = "文件长度不一致，期望 %d，实际 %d"
    private const val CHECKSUM_MISMATCH_FORMAT = "文件校验失败，%s 不匹配"

    /** 生成文件长度不匹配文案。 */
    fun fileLengthMismatch(expectedBytes: Long, actualBytes: Long): String = FILE_LENGTH_MISMATCH_FORMAT.format(expectedBytes, actualBytes)

    /** 生成校验值不匹配文案。 */
    fun checksumMismatch(checksumType: String): String = CHECKSUM_MISMATCH_FORMAT.format(checksumType.uppercase())
}
