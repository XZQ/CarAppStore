package com.nio.appstore.domain.upgrade

interface UpgradeManager {
    /** 启动指定应用的升级流程。 */
    suspend fun startUpgrade(appId: String)

    /** 检查指定应用当前是否存在可升级版本。 */
    suspend fun checkUpgrade(appId: String): Boolean
}
