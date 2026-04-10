package com.nio.appstore.data.local.entity

/**
 * SettingsEntity 描述本地持久化的单个设置项。
 */
data class SettingsEntity(
    /** 稳定的设置项键名。 */
    val key: String,
    /** 持久化保存的设置值。 */
    val value: String,
    /** 最后更新时间戳。 */
    val updatedAt: Long,
)
