package com.xzq.appstore.domain.upgrade

interface UpgradeManager {
    /** 启动指定应用的升级流程。 */
    suspend fun startUpgrade(appId: String)

    /** 检查指定应用当前是否存在可升级版本。 */
    suspend fun checkUpgrade(appId: String): Boolean

    /** 检查全部已安装应用是否存在可升级版本，返回有升级可用的 appId 列表。 */
    suspend fun checkAllUpgrades(): List<String>

    /** 批量启动升级流程，逐个执行升级直到全部完成或遇到失败，并返回每个应用的最终结果。 */
    suspend fun startBatchUpgrade(appIds: List<String>): UpgradeBatchResult
}

/**
 * 批量升级的汇总结果。
 *
 * @property succeeded 实际走完升级流程并成功的 appId 列表。
 * @property failed 升级失败的 appId 与对应错误信息。
 * @property skipped 因策略或前置条件拒绝而未执行的 appId 与原因。
 */
data class UpgradeBatchResult(
    val succeeded: List<String> = emptyList(),
    val failed: Map<String, String> = emptyMap(),
    val skipped: Map<String, String> = emptyMap(),
) {
    /** 是否全部成功。 */
    val allSucceeded: Boolean get() = failed.isEmpty() && skipped.isEmpty()
}
