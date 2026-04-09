package com.nio.appstore.common.navigation

/**
 * MainNavigator 是 feature 页面依赖的统一导航接口。
 *
 * 接口放到 common 后，feature 不再依赖 app 壳层中的具体实现，
 * app 只需要负责实现这个接口即可。
 */
interface MainNavigator {
    fun openHome()
    fun openSearch()
    fun openDownloadManager()
    fun openUpgradeManager()
    fun openInstallManager()
    fun openDeveloperSettings()
    fun openDetail(appId: String)
    fun openMyApps()
    fun updateTitle(title: String)
}
