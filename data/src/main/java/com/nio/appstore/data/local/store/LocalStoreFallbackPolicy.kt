package com.nio.appstore.data.local.store

object LocalStoreFallbackPolicy {
    fun <T> preferFacade(
        facadeValue: T?,
        legacyValue: T?,
    ): T? = facadeValue ?: legacyValue

    fun <T> preferFacadeList(
        facadeValues: List<T>,
        legacyValues: List<T>,
    ): List<T> = if (facadeValues.isNotEmpty()) facadeValues else legacyValues
}
