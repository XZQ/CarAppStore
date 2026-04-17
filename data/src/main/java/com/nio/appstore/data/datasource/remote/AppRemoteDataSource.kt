package com.nio.appstore.data.datasource.remote

import android.content.Context
import com.nio.appstore.core.logger.AppLogger
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.UpgradeInfo
import java.io.File

class AppRemoteDataSource(
    context: Context,
    /** 当前环境下的下载源目录。 */
    private val sourceCatalog: DownloadSourceCatalog,
    /** 当前环境下商店目录接口地址。 */
    catalogEndpointUrl: String? = null,
    /** 目录 HTTP 客户端。 */
    httpClient: AppCatalogHttpClient = HttpUrlConnectionAppCatalogHttpClient(),
    /** 商店目录缓存文件。 */
    catalogCacheFile: File? = null,
) {
    /** 远端目录读取器。 */
    private val catalogLoader = AppRemoteCatalogLoader(context)
    /** 商店目录数据源。 */
    private val catalogSource: AppCatalogSource = ResilientAppCatalogSource(
        loader = catalogLoader,
        endpointUrl = catalogEndpointUrl,
        httpClient = httpClient,
        cacheFile = catalogCacheFile,
        fallbackSource = ResourceAppCatalogSource(catalogLoader),
        logger = AppLogger(),
    )

    /** 返回首页应用列表。 */
    suspend fun getHomeApps(): List<AppInfo> = loadCatalog().map { it.appInfo }

    /** 根据 appId 返回应用详情，并补全当前环境下的下载源信息。 */
    suspend fun getAppDetail(appId: String): AppDetail {
        val app = findItem(appId)
        // 详情数据中的下载地址、校验值和下载策略统一来自下载源目录。
        val source = sourceCatalog.get(app.appId)
        return app.appDetail.copy(
            apkUrl = source.apkUrl,
            checksumType = source.checksumType,
            checksumValue = source.checksumValue,
            sourcePolicy = source.sourcePolicy,
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
        return requireNotNull(loadCatalog().firstOrNull { it.appId == appId }) {
            "未找到 appId=$appId 对应的远端目录项"
        }
    }

    /** 加载当前生效的商店目录。 */
    private suspend fun loadCatalog(): List<RemoteCatalogItem> = catalogSource.load()
}
