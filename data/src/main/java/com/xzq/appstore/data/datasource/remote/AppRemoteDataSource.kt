package com.xzq.appstore.data.datasource.remote

import android.content.Context
import com.xzq.appstore.common.grayscale.GrayscaleHeaderStore
import com.xzq.appstore.core.logger.AppLogger
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.AppInfo
import com.xzq.appstore.data.model.UpgradeInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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
    /** 目录内存缓存使用的单调时钟；测试可注入以覆盖 TTL 边界。 */
    private val monotonicClockNanos: () -> Long = System::nanoTime,
    /** 目录信任元数据策略；生产与测试环境严格，Debug + LOCAL_SIM 放宽。 */
    catalogTrustPolicy: AppCatalogTrustPolicy = AppCatalogTrustPolicy.STRICT,
) {
    private val catalogChannel = catalogRequestHeaders["X-Client-Channel"].orEmpty()
    private val installationIdStore = CatalogInstallationIdStore(context.applicationContext.noBackupFilesDir)
    private val installationId by lazy { installationIdStore.getOrCreate() }

    /** 远端目录读取器：远端目录必须提供完整的 versionCode 与签名摘要。 */
    private val catalogLoader = AppRemoteCatalogLoader(context, catalogTrustPolicy)

    /**
     * 内置 raw 目录读取器。
     *
     * raw 目录随 APK 一起分发、由构建方维护，缺少签名摘要时仍要保证离线可浏览；
     * 真正的安装校验由 APK 校验策略负责，因此这里固定使用宽松策略。
     */
    private val fallbackCatalogLoader = AppRemoteCatalogLoader(context, AppCatalogTrustPolicy.RELAXED)

    /** 商店目录数据源。 */
    private val catalogSource: ResilientAppCatalogSource = ResilientAppCatalogSource(
        loader = catalogLoader,
        endpointUrl = catalogEndpointUrl,
        requestHeaders = catalogRequestHeaders,
        grayscaleHeaderProvider = { GrayscaleHeaderStore.read(context.applicationContext) },
        httpClient = httpClient,
        cacheFile = catalogCacheFile,
        cacheMetadataFile = catalogCacheMetadataFile,
        fallbackSource = ResourceAppCatalogSource(fallbackCatalogLoader),
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
        // 复用已找到的目录项补全下载源，避免 getAppDetail 内部再做一次目录查找。
        val app = findItem(appId)
        val source = sourceCatalog.get(app.appId)
        val detail = app.appDetail
        return app.upgradeInfo.copy(
            apkUrl = detail.apkUrl.ifBlank { source.apkUrl },
        )
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
     *
     * 缓存键包含请求维度摘要：灰度分组切换后立即失效，不会把切换前的目录再发出去。
     * TTL 按来源区分：只有远端权威数据享受完整 TTL，磁盘缓存 / 内置目录属于降级结果，
     * 仅做短期合流（抵挡同一屏的并发调用），网络恢复后尽快回到远端。
     */
    private val catalogCacheLock = Mutex()
    private var cachedVisibleCatalog: VisibleCatalogCache? = null

    /** 加载当前生效的商店目录。 */
    private suspend fun loadVisibleCatalog(): List<RemoteCatalogItem> = catalogCacheLock.withLock {
        val nowNanos = monotonicClockNanos()
        val fingerprint = catalogSource.currentRequestFingerprint()
        cachedVisibleCatalog
            ?.takeIf { it.fingerprint == fingerprint && it.isFresh(nowNanos) }
            ?.let { return@withLock it.items }

        val result = catalogSource.loadWithOrigin()
        val fresh = result.items.filter { item ->
            val releaseId = item.appDetail.versionCode.takeIf { it > 0 }?.let { "code:$it" }
                ?: "name:${item.appDetail.versionName}"
            item.governance.isVisible(item.appId, catalogChannel, installationId, releaseId)
        }
        cachedVisibleCatalog = VisibleCatalogCache(
            items = fresh,
            fingerprint = fingerprint,
            origin = result.origin,
            loadedAtNanos = nowNanos,
        )
        fresh
    }

    /** 一次目录加载在内存中的快照。 */
    private data class VisibleCatalogCache(
        /** 过滤后的可见目录。 */
        val items: List<RemoteCatalogItem>,
        /** 加载时的请求维度摘要（含灰度分组）。 */
        val fingerprint: String,
        /** 数据来源。 */
        val origin: AppCatalogOrigin,
        /** 加载时刻的单调时钟读数。 */
        val loadedAtNanos: Long,
    ) {
        /** 单调时钟回退（elapsed < 0）时视为过期，强制重新加载。 */
        fun isFresh(nowNanos: Long): Boolean {
            val elapsedNanos = nowNanos - loadedAtNanos
            return elapsedNanos >= 0L && elapsedNanos < ttlNanos()
        }

        private fun ttlNanos(): Long = when (origin) {
            AppCatalogOrigin.NETWORK -> NETWORK_CACHE_TTL_NANOS
            AppCatalogOrigin.DISK_CACHE, AppCatalogOrigin.RESOURCE -> DEGRADED_CACHE_TTL_NANOS
        }
    }

    private companion object {
        /** 远端目录的内存缓存有效期。弱网场景下 30s 的轻微延迟可接受，同时避免频繁全量重拉。 */
        const val NETWORK_CACHE_TTL_NANOS = 30_000_000_000L

        /**
         * 降级目录（磁盘缓存 / 内置资源）只做短期合流。
         *
         * 取 3s：足以让同一屏的 N 次并发查询共用一次失败结果，又不会把「刚才断网」
         * 的状态固定 30s，网络恢复后下一次调用就会重新请求远端。
         */
        const val DEGRADED_CACHE_TTL_NANOS = 3_000_000_000L
    }
}
