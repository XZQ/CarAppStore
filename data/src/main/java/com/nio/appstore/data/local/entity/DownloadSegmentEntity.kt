package com.nio.appstore.data.local.entity

data class DownloadSegmentEntity(
    /** 稳定的分片标识。 */
    val segmentId: String,
    /** 所属下载任务标识。 */
    val taskId: String,
    /** 分片在下载计划中的顺序。 */
    val index: Int,
    /** 分片的起始字节位置，含边界。 */
    val startByte: Long,
    /** 分片的结束字节位置，含边界。 */
    val endByte: Long,
    /** 当前分片已下载的字节数。 */
    val downloadedBytes: Long,
    /** 当前持久化的分片状态。 */
    val status: String,
    /** 当前分片临时文件路径。 */
    val tmpFilePath: String,
    /** 当前分片已经消耗的重试次数。 */
    val retryCount: Int = 0,
    /** 分片创建时间戳。 */
    val createdAt: Long,
    /** 最后更新时间戳。 */
    val updatedAt: Long,
)
