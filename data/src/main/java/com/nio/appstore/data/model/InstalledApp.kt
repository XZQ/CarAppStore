package com.nio.appstore.data.model

/**
 * InstalledApp 描述系统已安装应用的业务模型。
 */
data class InstalledApp(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 安卓包名。 */
    val packageName: String,
    /** 展示给用户的应用名称。 */
    val name: String,
    /** 当前已安装版本号。 */
    val versionName: String,
)
