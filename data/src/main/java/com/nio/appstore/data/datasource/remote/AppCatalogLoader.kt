package com.nio.appstore.data.datasource.remote

/**
 * AppCatalogLoader 定义商店目录解析与资源读取能力。
 */
interface AppCatalogLoader {
    /** 读取资源目录。 */
    fun loadFromResource(): List<RemoteCatalogItem>

    /** 解析指定目录文本。 */
    fun parse(rawText: String): List<RemoteCatalogItem>
}
