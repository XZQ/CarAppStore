package com.nio.appstore.domain.install

interface InstallManager {
    /** 启动指定应用的安装流程。 */
    suspend fun install(appId: String)

    /** 清理指定应用的安装失败态，并恢复到可继续操作的状态。 */
    suspend fun clearFailed(appId: String)
}
