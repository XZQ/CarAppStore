package com.nio.appstore.data.datasource.remote

import org.json.JSONObject
import java.io.File

/**
 * AppCatalogCacheMetadata 描述目录缓存关联的 HTTP 校验信息。
 */
data class AppCatalogCacheMetadata(
    /** 上次成功目录请求返回的 ETag。 */
    val eTag: String? = null,
    /** 上次成功目录请求返回的 Last-Modified。 */
    val lastModified: String? = null,
)

/**
 * AppCatalogCacheMetadataStore 负责读写目录缓存的校验元数据。
 */
object AppCatalogCacheMetadataStore {
    /** 从本地文件读取目录缓存元数据。 */
    fun read(file: File): AppCatalogCacheMetadata? {
        if (!file.exists()) return null
        val rawText = file.readText(Charsets.UTF_8)
        if (rawText.isBlank()) return null
        val json = JSONObject(rawText)
        return AppCatalogCacheMetadata(
            eTag = json.optString(KEY_ETAG).takeIf { it.isNotBlank() },
            lastModified = json.optString(KEY_LAST_MODIFIED).takeIf { it.isNotBlank() },
        )
    }

    /** 把目录缓存元数据写入本地文件。 */
    fun write(file: File, metadata: AppCatalogCacheMetadata) {
        file.parentFile?.mkdirs()
        val json = JSONObject().apply {
            put(KEY_ETAG, metadata.eTag)
            put(KEY_LAST_MODIFIED, metadata.lastModified)
        }
        file.writeText(json.toString(), Charsets.UTF_8)
    }

    private const val KEY_ETAG = "etag"
    private const val KEY_LAST_MODIFIED = "lastModified"
}
