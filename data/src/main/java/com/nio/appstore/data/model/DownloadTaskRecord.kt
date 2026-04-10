package com.nio.appstore.data.model

import com.nio.appstore.domain.state.DownloadStatus

/**
 * DownloadTaskRecord 描述数据层持久化的下载任务记录。
 */
data class DownloadTaskRecord(
    /** 稳定的任务标识。 */
    val taskId: String,
    /** 与任务绑定的应用标识。 */
    val appId: String,
    /** 当前持久化的下载状态。 */
    val status: DownloadStatus,
    /** 当前进度百分比。 */
    val progress: Int,
    /** 最终目标安装包文件路径。 */
    val targetFilePath: String,
    /** 已经落盘的字节数。 */
    val downloadedBytes: Long,
    /** 已知时的期望总字节数。 */
    val totalBytes: Long,
    /** 最近一次测速得到的每秒速率。 */
    val speedBytesPerSec: Long = 0L,
    /** 当前错误状态对应的稳定失败码。 */
    val failureCode: String? = null,
    /** 展示给用户的失败详情。 */
    val failureMessage: String? = null,
    /** 当前任务已经消耗的重试次数。 */
    val retryCount: Int = 0,
    /** 任务使用的下载地址。 */
    val downloadUrl: String? = null,
    /** 启用分片下载时使用的临时目录路径。 */
    val tempDirPath: String? = null,
    /** 用于探测远端文件变化的缓存 ETag。 */
    val eTag: String? = null,
    /** 用于探测远端文件变化的缓存 Last-Modified。 */
    val lastModified: String? = null,
    /** 远端是否支持 Range 请求。 */
    val supportsRange: Boolean = false,
    /** 最终校验时使用的可选校验算法。 */
    val checksumType: String? = null,
    /** 最终校验时使用的可选校验值。 */
    val checksumValue: String? = null,
    /** 任务计划拆分的分片数量。 */
    val segmentCount: Int = 1,
    /** 任务创建时间戳。 */
    val createdAt: Long,
    /** 最后更新时间戳。 */
    val updatedAt: Long,
)
