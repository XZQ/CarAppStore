package com.nio.appstore.data.datasource.remote

import android.content.Context
import com.nio.appstore.data.R
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.UpgradeInfo

/**
 * AppRemoteCatalogLoader 负责从资源目录读取演示商店目录，并转换为领域模型。
 */
class AppRemoteCatalogLoader(
    /** 用于读取 raw 目录资源的应用级上下文。 */
    context: Context,
) : AppCatalogLoader {
    /** 应用级上下文，避免持有页面级引用。 */
    private val appContext = context.applicationContext

    /** 读取并解析 raw 目录中的远端目录。 */
    override fun loadFromResource(): List<RemoteCatalogItem> {
        val rawText = appContext.resources.openRawResource(R.raw.app_store_catalog)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return parse(rawText)
    }

    /** 解析指定文本内容。 */
    override fun parse(rawText: String): List<RemoteCatalogItem> = AppCatalogJsonParser.parse(rawText)
}

/**
 * RemoteCatalogItem 描述商店目录中单个应用的完整远端数据。
 */
data class RemoteCatalogItem(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 列表页使用的轻量信息。 */
    val appInfo: AppInfo,
    /** 详情页使用的完整信息。 */
    val appDetail: AppDetail,
    /** 升级页使用的版本信息。 */
    val upgradeInfo: UpgradeInfo,
)
