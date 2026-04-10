package com.nio.appstore.data.local.store

object LocalStoreFallbackPolicy {
    /** 标量值读取时优先使用结构化 store，缺失时回退 legacy 值。 */
    fun <T> preferFacade(
        facadeValue: T?,
        legacyValue: T?,
    ): T? = facadeValue ?: legacyValue

    /** 列表值读取时优先使用结构化 store，缺失时回退 legacy 列表。 */
    fun <T> preferFacadeList(
        facadeValues: List<T>,
        legacyValues: List<T>,
    ): List<T> = if (facadeValues.isNotEmpty()) facadeValues else legacyValues
}
