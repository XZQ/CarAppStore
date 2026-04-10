package com.nio.appstore.common.navigation

/**
 * MainNavigator 是 feature 页面依赖的统一导航接口。
 *
 * 接口放到 common 后，feature 不再依赖 app 壳层中的具体实现，
 * app 只需要负责实现这个接口即可。
 */
interface MainNavigator {
    /** 切换到首页。 */
    fun openHome()
    /** 切换到搜索页。 */
    fun openSearch()
    /** 切换到下载中心。 */
    fun openDownloadManager()
    /** 切换到升级中心。 */
    fun openUpgradeManager()
    /** 切换到安装中心。 */
    fun openInstallManager()
    /** 切换到开发设置页。 */
    fun openDeveloperSettings()
    /** 打开指定应用详情页。 */
    fun openDetail(appId: String)
    /** 切换到我的应用页。 */
    fun openMyApps()
    /** 更新当前宿主页面标题。 */
    fun updateTitle(title: String)
}
