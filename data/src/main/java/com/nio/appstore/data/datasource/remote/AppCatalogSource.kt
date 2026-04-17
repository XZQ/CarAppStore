package com.nio.appstore.data.datasource.remote

import com.nio.appstore.core.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AppCatalogSource 定义远端商店目录的统一读取入口。
 */
interface AppCatalogSource {
    /** 加载当前可用的商店目录。 */
    suspend fun load(): List<RemoteCatalogItem>
}

/**
 * ResourceAppCatalogSource 从 raw 目录加载商店目录。
 */
class ResourceAppCatalogSource(
    /** 目录解析器。 */
    private val loader: AppCatalogLoader,
) : AppCatalogSource {
    /** 读取资源目录。 */
    override suspend fun load(): List<RemoteCatalogItem> = loader.loadFromResource()
}

/**
 * ResilientAppCatalogSource 按“HTTP -> 缓存 -> 资源”顺序加载商店目录。
 */
class ResilientAppCatalogSource(
    /** 目录解析器。 */
    private val loader: AppCatalogLoader,
    /** 目录接口地址。 */
    private val endpointUrl: String?,
    /** 目录 HTTP 客户端。 */
    private val httpClient: AppCatalogHttpClient,
    /** 目录缓存文件。 */
    private val cacheFile: File?,
    /** 资源目录兜底源。 */
    private val fallbackSource: AppCatalogSource,
    /** 日志入口。 */
    private val logger: AppLogger = AppLogger(),
) : AppCatalogSource {

    /** 加载商店目录。 */
    override suspend fun load(): List<RemoteCatalogItem> {
        val httpCatalog = loadFromHttp()
        if (httpCatalog != null) return httpCatalog

        val cacheCatalog = loadFromCache()
        if (cacheCatalog != null) return cacheCatalog

        return fallbackSource.load()
    }

    /** 尝试通过 HTTP 获取目录。 */
    private suspend fun loadFromHttp(): List<RemoteCatalogItem>? {
        if (endpointUrl.isNullOrBlank()) return null
        return runCatching {
            val responseText = httpClient.fetch(requireNotNull(endpointUrl))
            val catalog = loader.parse(responseText)
            withContext(Dispatchers.IO) {
                cacheFile?.writeText(responseText, Charsets.UTF_8)
            }
            catalog
        }.onFailure {
            logger.d(TAG, "load catalog from http failed: ${it.message}")
        }.getOrNull()
    }

    /** 尝试从缓存文件恢复目录。 */
    private suspend fun loadFromCache(): List<RemoteCatalogItem>? {
        val target = cacheFile?.takeIf { it.exists() } ?: return null
        return runCatching {
            val cachedText = readCache(target)
            loader.parse(cachedText)
        }.onFailure {
            logger.d(TAG, "load catalog from cache failed: ${it.message}")
        }.getOrNull()
    }

    /** 在 IO 线程读取缓存文本。 */
    private suspend fun readCache(target: File): String {
        return withContext(Dispatchers.IO) { target.readText(Charsets.UTF_8) }
    }

    private companion object {
        private const val TAG = "AppCatalogSource"
    }
}
