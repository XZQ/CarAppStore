package com.nio.appstore.core.downloader

data class DownloadRemoteMeta(
    val contentLength: Long = -1L,
    val eTag: String? = null,
    val lastModified: String? = null,
    val supportsRange: Boolean = false,
    val mimeType: String? = null,
)
