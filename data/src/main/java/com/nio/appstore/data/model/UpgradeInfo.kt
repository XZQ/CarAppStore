package com.nio.appstore.data.model

/**
 * UpgradeInfo 描述指定应用的升级信息。
 */
data class UpgradeInfo(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 当前可升级到的最新版本。 */
    val latestVersion: String,
    /** 执行升级链路时使用的安装包地址。 */
    val apkUrl: String,
    /** 当前是否存在可升级版本。 */
    val hasUpgrade: Boolean,
)
