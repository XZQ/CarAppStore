package com.nio.appstore.data.model

/**
 * AppInfo 描述首页和搜索页使用的轻量应用信息。
 */
data class AppInfo(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 应用对应的安卓包名。 */
    val packageName: String,
    /** 展示给用户的应用名称。 */
    val name: String,
    /** 列表中展示的简要描述。 */
    val description: String,
    /** 远端数据源当前给出的版本号。 */
    val versionName: String,
)
