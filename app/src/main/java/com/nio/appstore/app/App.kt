package com.nio.appstore.app

import android.app.Application
import com.nio.appstore.common.base.AppContainerProvider
import com.nio.appstore.common.base.AppServices

/**
 * App 是应用级入口，同时实现 AppContainerProvider，
 * 供 common/base 与各 feature 页面获取全局服务入口。
 */
class App : Application(), AppContainerProvider {

    /** 应用进程内共享的依赖装配容器。 */
    lateinit var appContainer: AppContainer
        private set

    /** 对外暴露的全局服务入口，供 common 与 feature 层读取。 */
    override val appServices: AppServices
        get() = appContainer

    /** 在应用启动时完成全局依赖初始化。 */
    override fun onCreate() {
        super.onCreate()
        // 应用启动后立刻装配容器，保证后续页面能直接获取业务服务。
        appContainer = AppContainer(this)
    }
}
