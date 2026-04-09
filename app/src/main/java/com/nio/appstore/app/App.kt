package com.nio.appstore.app

import android.app.Application
import com.nio.appstore.common.base.AppContainerProvider
import com.nio.appstore.common.base.AppServices

/**
 * App 是应用级入口，同时实现 AppContainerProvider，
 * 供 common/base 与各 feature 页面获取全局服务入口。
 */
class App : Application(), AppContainerProvider {

    lateinit var appContainer: AppContainer
        private set

    override val appServices: AppServices
        get() = appContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
