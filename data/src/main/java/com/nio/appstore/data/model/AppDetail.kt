package com.nio.appstore.data.model

import com.nio.appstore.core.downloader.DownloadSourcePolicy

/**
 * AppDetail 描述详情页和下载链路需要的完整应用信息。
 */
data class AppDetail(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 应用对应的安卓包名。 */
    val packageName: String,
    /** 展示给用户的应用名称。 */
    val name: String,
    /** 详情页展示的应用描述。 */
    val description: String,
    /** 应用商店当前主推的版本号。 */
    val versionName: String,
    /** 应用开发者名称。 */
    val developerName: String = "",
    /** 应用所属分类。 */
    val category: String = "",
    /** 商店展示评分文案。 */
    val ratingText: String = "",
    /** 应用包体大小文案。 */
    val sizeText: String = "",
    /** 最近更新时间文案。 */
    val lastUpdatedText: String = "",
    /** 兼容性说明。 */
    val compatibilitySummary: String = "",
    /** 权限与隐私说明。 */
    val permissionsSummary: String = "",
    /** 更新日志摘要。 */
    val updateSummary: String = "",
    /** 安装包的下载地址。 */
    val apkUrl: String,
    /** 可选的安装包校验算法。 */
    val checksumType: String? = null,
    /** 可选的安装包期望校验值。 */
    val checksumValue: String? = null,
    /** 解析下载源时优先采用的策略。 */
    val sourcePolicy: DownloadSourcePolicy = DownloadSourcePolicy.FALLBACK_SIMULATED,
)
