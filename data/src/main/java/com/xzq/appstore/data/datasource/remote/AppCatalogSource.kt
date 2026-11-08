package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.common.grayscale.GrayscaleHeaderStore
import com.xzq.appstore.core.logger.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 目录数据的实际来源，供上层判断结果是否可信、能否长期缓存。 */
enum class AppCatalogOrigin {
    /** 本次请求从远端成功获取（含 304 复用本地缓存）。 */
    NETWORK,

    /** 远端不可用，复用磁盘缓存。 */
    DISK_CACHE,

    /** 远端与磁盘缓存都不可用，退回安装包内置目录。 */
    RESOURCE,
}

/** 目录加载结果：数据本身 + 来源，来源用于区分「权威数据」与「降级兜底」。 */
data class AppCatalogLoadResult(
    /** 目录条目。 */
    val items: List<RemoteCatalogItem>,
    /** 数据来源。 */
    val origin: AppCatalogOrigin,
)

/**
 * AppCatalogSource 定义远端商店目录的统一读取入口。
 */
interface AppCatalogSource {
    /** 加载当前可用的商店目录。 */
    suspend fun load(): List<RemoteCatalogItem>

    /**
     * 加载目录并附带来源信息。
     *
     * 默认实现把结果视为网络来源，仅用于只读兜底源；主链路实现必须覆盖本方法，
     * 否则降级数据会被当成权威数据缓存。
     */
    suspend fun loadWithOrigin(): AppCatalogLoadResult = AppCatalogLoadResult(load(), AppCatalogOrigin.NETWORK)
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
    /** 目录请求头配置。 */
    private val requestHeaders: Map<String, String>,
    /** 目录 HTTP 客户端。 */
    private val httpClient: AppCatalogHttpClient,
    /** 目录缓存文件。 */
    private val cacheFile: File?,
    /** 目录缓存元数据文件。 */
    private val cacheMetadataFile: File?,
    /** 资源目录兜底源。 */
    private val fallbackSource: AppCatalogSource,
    /** 实时提供灰度头配置，由调用方从共享存储读取。 */
    private val grayscaleHeaderProvider: () -> GrayscaleHeaderStore.GrayscaleHeaderConfig = {
        GrayscaleHeaderStore.GrayscaleHeaderConfig(enabled = false, tag = "")
    },
    /** 日志入口。 */
    private val logger: AppLogger = AppLogger(),
) : AppCatalogSource {
    /** 加载商店目录，保留旧口径的便捷入口。 */
    override suspend fun load(): List<RemoteCatalogItem> = loadWithOrigin().items

    /** 加载商店目录并标注来源：HTTP -> 磁盘缓存 -> 内置资源。 */
    override suspend fun loadWithOrigin(): AppCatalogLoadResult {
        val request = createRequest()
        val fingerprint = AppCatalogCacheIdentity.requestFingerprint(request)
        val cached = loadFromCache(fingerprint)
        loadFromHttp(request, fingerprint, cached)?.let { return AppCatalogLoadResult(it, AppCatalogOrigin.NETWORK) }
        cached?.let { return AppCatalogLoadResult(it.catalog, AppCatalogOrigin.DISK_CACHE) }
        return AppCatalogLoadResult(fallbackSource.load(), AppCatalogOrigin.RESOURCE)
    }

    /**
     * 当前请求维度摘要（接口地址 + 请求头，含灰度分组）。
     *
     * 上游内存缓存以该值作为键：灰度分组切换后必须立刻重新加载，
     * 不能继续复用切换前的目录。
     */
    fun currentRequestFingerprint(): String = AppCatalogCacheIdentity.requestFingerprint(createRequest())

    /** 每次读取生效灰度配置，并用相同的请求维度判断缓存能否复用。 */
    private fun createRequest(): AppCatalogHttpRequest {
        val grayscaleHeader = try {
            GrayscaleHeaderStore.toHeader(grayscaleHeaderProvider())
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (failure: Exception) {
            logger.d(TAG, "load grayscale configuration failed: ${failure.message}")
            null
        }
        return AppCatalogHttpRequest(
            endpointUrl = endpointUrl.orEmpty(),
            headers = if (grayscaleHeader != null) requestHeaders + grayscaleHeader else requestHeaders,
        )
    }

    /** 仅对完整且属于当前请求的缓存发条件请求，缓存丢失时恢复全量响应。 */
    private suspend fun loadFromHttp(
        request: AppCatalogHttpRequest,
        fingerprint: String,
        cached: CachedCatalog?,
    ): List<RemoteCatalogItem>? {
        if (request.endpointUrl.isBlank()) return null
        return try {
            var response = httpClient.fetch(request.copy(eTag = cached?.metadata?.eTag, lastModified = cached?.metadata?.lastModified))
            if (response.notModified && cached != null) {
                val metadata = cached.metadata.copy(
                    eTag = response.eTag ?: cached.metadata.eTag,
                    lastModified = response.lastModified ?: cached.metadata.lastModified,
                )
                persistCache(cached.body, metadata)
                return cached.catalog
            }
            if (response.notModified) response = httpClient.fetch(request)
            require(!response.notModified) { "catalog returned 304 without a reusable cache" }
            val responseText = requireNotNull(response.body) { "catalog response body is empty" }
            val catalog = loader.parse(responseText)
            persistCache(responseText, AppCatalogCacheMetadata(
                eTag = response.eTag,
                lastModified = response.lastModified,
                requestFingerprint = fingerprint,
                bodySha256 = AppCatalogCacheIdentity.bodySha256(responseText),
            ))
            catalog
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (failure: Exception) {
            logger.d(TAG, "load catalog from http failed: ${failure.message}")
            null
        }
    }

    /** 缺失、损坏、旧版或其他环境的元数据只使缓存失效，仍继续请求远端。 */
    private suspend fun loadFromCache(fingerprint: String): CachedCatalog? {
        return try {
            withContext(Dispatchers.IO) {
                val target = cacheFile ?: return@withContext null
                val metadata = cacheMetadataFile?.let(AppCatalogCacheMetadataStore::read) ?: return@withContext null
                if (metadata.requestFingerprint != fingerprint || !target.exists()) return@withContext null
                val body = target.readText(Charsets.UTF_8)
                if (metadata.bodySha256 != AppCatalogCacheIdentity.bodySha256(body)) return@withContext null
                CachedCatalog(body, loader.parse(body), metadata)
            }
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (failure: Exception) {
            logger.d(TAG, "load catalog from cache failed: ${failure.message}")
            null
        }
    }

    /** 缓存写入失败不丢弃已成功获取的目录；正文摘要使中断写入不会复用错配数据。 */
    private suspend fun persistCache(body: String, metadata: AppCatalogCacheMetadata) {
        try {
            withContext(Dispatchers.IO) {
                if (cacheFile == null || cacheMetadataFile == null) return@withContext
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(body, Charsets.UTF_8)
                AppCatalogCacheMetadataStore.write(cacheMetadataFile, metadata)
            }
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (failure: Exception) {
            logger.d(TAG, "persist catalog cache failed: ${failure.message}")
        }
    }

    private data class CachedCatalog(
        val body: String,
        val catalog: List<RemoteCatalogItem>,
        val metadata: AppCatalogCacheMetadata,
    )

    private companion object {
        private const val TAG = "AppCatalogSource"
    }
}
