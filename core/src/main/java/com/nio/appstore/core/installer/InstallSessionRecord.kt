package com.nio.appstore.core.installer

data class InstallSessionRecord(
    /** 平台分配的安装会话标识。 */
    val sessionId: Int = -1,
    /** 稳定的应用标识。 */
    val appId: String,
    /** 安卓包名。 */
    val packageName: String,
    /** 与安装会话绑定的本地安装包路径。 */
    val apkPath: String,
    /** 安装完成后期望达到的目标版本。 */
    val targetVersion: String,
    /** 当前持久化的安装会话状态。 */
    val status: String,
    /** 当前安装会话进度百分比。 */
    val progress: Int = 0,
    /** 当前安装会话对应的稳定失败码。 */
    val failureCode: String? = null,
    /** 展示给用户的失败详情。 */
    val failureMessage: String? = null,
    /** 安装会话创建时间戳。 */
    val createdAt: Long,
    /** 最后更新时间戳。 */
    val updatedAt: Long,
)
