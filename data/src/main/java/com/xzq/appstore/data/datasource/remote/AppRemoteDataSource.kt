package com.xzq.appstore.data.datasource.remote

import android.content.Context
import com.xzq.appstore.common.grayscale.GrayscaleHeaderStore
import com.xzq.appstore.core.logger.AppLogger
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.AppInfo
import com.xzq.appstore.data.model.UpgradeInfo
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppRemoteDataSource(
    context: Context,
    /** 当前环境下的下载源目录。 */
    private val sourceCatalog: DownloadSourceCatalog,
    /** 当前环境下商店目录接口地址。 */
    catalogEndpointUrl: String? = null,
    /** 当前环境下商店目录附加请求头。 */
    catalogRequestHeaders: Map<String, String> = emptyMap(),
    /** 目录 HTTP 客户端。 */
    httpClient: AppCatalogHttpClient = HttpUrlConnectionAppCatalogHttpClient(),
    /** 商店目录缓存文件。 */
    catalogCacheFile: File? = null,
    /** 商店目录缓存元数据文件。 */
    catalogCacheMetadataFile: File? = null,
) {
    private val catalogChannel = catalogRequestHeaders["X-Client-Channel"].orEmpty()
    /** 远端目录读取器。 */
    private val catalogLoader = AppRemoteCatalogLoader(context)
    /** 商店目录数据源。 */
    private val catalogSource: AppCatalogSource = ResilientAppCatalogSource(
        loader = catalogLoader,
        endpointUrl = catalogEndpointUrl,
        requestHeaders = catalogRequestHeaders,
        grayscaleHeaderProvider = { GrayscaleHeaderStore.read(context.applicationContext) },
        httpClient = httpClient,
        cacheFile = catalogCacheFile,
        cacheMetadataFile = catalogCacheMetadataFile,
        fallbackSource = ResourceAppCatalogSource(catalogLoader),
        logger = AppLogger(),
    )

    /** 返回首页应用列表。 */
    suspend fun getHomeApps(): List<AppInfo> = loadVisibleCatalog().map { it.appInfo }

    /** 根据 appId 返回应用详情，并补全当前环境下的下载源信息。 */
    suspend fun getAppDetail(appId: String): AppDetail {
        val app = findItem(appId)
        // 详情数据中的下载地址、校验值和下载策略统一来自下载源目录。
        val source = sourceCatalog.get(app.appId)
        return app.appDetail.copy(
            apkUrl = app.appDetail.apkUrl.ifBlank { source.apkUrl },
            checksumType = app.appDetail.checksumType ?: source.checksumType,
            checksumValue = app.appDetail.checksumValue ?: source.checksumValue,
            sourcePolicy = app.appDetail.sourcePolicy.takeIf { app.appDetail.apkUrl.isNotBlank() } ?: source.sourcePolicy,
        )
    }

    /** 返回指定应用的升级信息。 */
    suspend fun getUpgradeInfo(appId: String): UpgradeInfo {
        val app = findItem(appId)
        val detail = getAppDetail(appId)
        return app.upgradeInfo.copy(apkUrl = detail.apkUrl)
    }

    /** 查找指定应用的远端目录项。 */
    private suspend fun findItem(appId: String): RemoteCatalogItem {
        return requireNotNull(loadVisibleCatalog().firstOrNull { it.appId == appId }) {
            "未找到 appId=$appId 对应的远端目录项"
        }
    }

    /**
     * 生效目录的内存缓存（带 TTL）。
     *
     * 修复 N+1 目录加载风暴：`getHomeApps` 会为每个应用调用 `getUpgradeInfo`/`findItem`，
     * 这些调用原本每次都会触发一次完整的目录加载（HTTP→缓存→资源 + 整份 JSON 解析）。
     * 引入 TTL 缓存后，同一个时间窗口内只加载一次目录，其余调用直接复用，
     * 首页 N 个应用从「1+N 次整目录加载」降为「1 次加载 + N 次内存查找」。
     */
    private val catalogCacheLock = Mutex()
    private var cachedVisibleCatalog: List<RemoteCatalogItem>? = null
    private var cachedVisibleCatalogAt: Long = 0L

    /** 目录内存缓存有效期。弱网/车机场景下 30s 的轻微延迟可接受，同时避免频繁全量重拉。 */
    private val CATALOG_CACHE_TTL_MS = 30_000L

    /** 加载当前生效的商店目录。 */
    private suspend fun loadCatalog(): List<RemoteCatalogItem> = catalogSource.load()

    private suspend fun loadVisibleCatalog(): List<RemoteCatalogItem> = catalogCacheLock.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedVisibleCatalog
        if (cached != null && now - cachedVisibleCatalogAt < CATALOG_CACHE_TTL_MS) {
            return@withLock cached
        }
        val fresh = loadCatalog().filter { item -> item.governance.isVisible(item.appId, catalogChannel) }
        cachedVisibleCatalog = fresh
        cachedVisibleCatalogAt = now
        fresh
    }
}
