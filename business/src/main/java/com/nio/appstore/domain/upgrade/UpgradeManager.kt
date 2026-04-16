package com.nio.appstore.domain.upgrade

interface UpgradeManager {
    /** 启动指定应用的升级流程。 */
    suspend fun startUpgrade(appId: String)

    /** 检查指定应用当前是否存在可升级版本。 */
    suspend fun checkUpgrade(appId: String): Boolean

    /** 检查全部已安装应用是否存在可升级版本，返回有升级可用的 appId 列表。 */
    suspend fun checkAllUpgrades(): List<String>

    /** 批量启动升级流程，逐个执行升级直到全部完成或遇到失败。 */
    suspend fun startBatchUpgrade(appIds: List<String>)
}
