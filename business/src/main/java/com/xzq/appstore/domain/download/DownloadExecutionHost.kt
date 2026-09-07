package com.xzq.appstore.domain.download

/** 平台为活动下载提供执行期承载。获取失败时不能继续启动无保护的后台传输。 */
interface DownloadExecutionHost {
    suspend fun acquire(appId: String)
    suspend fun release(appId: String)

    /** 非 Android 宿主和隔离测试可按各自生命周期提供实现。 */
    object InProcess : DownloadExecutionHost {
        override suspend fun acquire(appId: String) = Unit
        override suspend fun release(appId: String) = Unit
    }
}
