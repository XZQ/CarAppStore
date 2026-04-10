package com.nio.appstore.common.base

/**
 * AppContainerProvider 用于把 app 壳层中的全局服务暴露给 common/base 与 feature 页面。
 */
interface AppContainerProvider {
    /** 暴露给 common/base 和 feature 层的全局服务集合。 */
    val appServices: AppServices
}
