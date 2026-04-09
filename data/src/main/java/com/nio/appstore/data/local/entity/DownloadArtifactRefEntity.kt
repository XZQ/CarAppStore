package com.nio.appstore.data.local.entity

data class DownloadArtifactRefEntity(
    /** 稳定的应用标识。 */
    val appId: String,
    /** 已下载产物对应的安装包持久化路径。 */
    val apkPath: String,
    /** 最近一次记录到的文件大小。 */
    val fileSize: Long = 0L,
    /** 当前产物文件是否仍然存在。 */
    val fileExists: Boolean = false,
    /** 最后更新时间戳。 */
    val updatedAt: Long,
)
