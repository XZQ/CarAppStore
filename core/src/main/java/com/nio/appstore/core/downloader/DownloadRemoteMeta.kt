package com.nio.appstore.core.downloader

data class DownloadRemoteMeta(
    /** 服务端返回的内容长度，未知时为 `-1`。 */
    val contentLength: Long = -1L,
    /** 服务端返回的 ETag，用于探测文件变化。 */
    val eTag: String? = null,
    /** 服务端返回的 Last-Modified 头。 */
    val lastModified: String? = null,
    /** 远端服务是否支持 Range 请求。 */
    val supportsRange: Boolean = false,
    /** 服务端返回的 MIME 类型。 */
    val mimeType: String? = null,
)
