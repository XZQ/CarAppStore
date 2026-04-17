package com.nio.appstore.data.datasource.remote

import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.UpgradeInfo
import com.nio.appstore.core.logger.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppCatalogSourceTest {

    @Test
    fun `load 在 HTTP 成功时返回远端目录并写入缓存`() = runBlocking {
        val workDir = Files.createTempDirectory("catalog-source-http").toFile()
        val cacheFile = File(workDir, "catalog.json")
        val source = ResilientAppCatalogSource(
            loader = FakeCatalogLoader(parsedCatalog = listOf(TEST_ITEM)),
            endpointUrl = "https://example.com/catalog.json",
            httpClient = FakeHttpClient(TEST_HTTP_RESPONSE),
            cacheFile = cacheFile,
            fallbackSource = FakeCatalogSource(listOf(FALLBACK_ITEM)),
            logger = QuietLogger(),
        )

        val result = source.load()

        assertEquals(listOf(TEST_ITEM), result)
        assertEquals(TEST_HTTP_RESPONSE, cacheFile.readText(Charsets.UTF_8))
    }

    @Test
    fun `load 在 HTTP 失败时回退到缓存`() = runBlocking {
        val workDir = Files.createTempDirectory("catalog-source-cache").toFile()
        val cacheFile = File(workDir, "catalog.json").apply {
            writeText(TEST_CACHE_RESPONSE, Charsets.UTF_8)
        }
        val source = ResilientAppCatalogSource(
            loader = FakeCatalogLoader(
                parsedCatalogByText = mapOf(TEST_CACHE_RESPONSE to listOf(CACHE_ITEM)),
            ),
            endpointUrl = "https://example.com/catalog.json",
            httpClient = FailingHttpClient(),
            cacheFile = cacheFile,
            fallbackSource = FakeCatalogSource(listOf(FALLBACK_ITEM)),
            logger = QuietLogger(),
        )

        val result = source.load()

        assertEquals(listOf(CACHE_ITEM), result)
    }

    @Test
    fun `load 在 HTTP 和缓存都失败时回退到资源目录`() = runBlocking {
        val workDir = Files.createTempDirectory("catalog-source-fallback").toFile()
        val cacheFile = File(workDir, "catalog.json").apply {
            writeText("broken-cache", Charsets.UTF_8)
        }
        val source = ResilientAppCatalogSource(
            loader = FakeCatalogLoader(),
            endpointUrl = "https://example.com/catalog.json",
            httpClient = FailingHttpClient(),
            cacheFile = cacheFile,
            fallbackSource = FakeCatalogSource(listOf(FALLBACK_ITEM)),
            logger = QuietLogger(),
        )

        val result = source.load()

        assertEquals(listOf(FALLBACK_ITEM), result)
    }

    private class FakeCatalogLoader(
        /** 默认解析结果。 */
        private val parsedCatalog: List<RemoteCatalogItem> = emptyList(),
        /** 按原始文本返回的解析结果。 */
        private val parsedCatalogByText: Map<String, List<RemoteCatalogItem>> = emptyMap(),
    ) : AppCatalogLoader {
        override fun loadFromResource(): List<RemoteCatalogItem> = parsedCatalog

        override fun parse(rawText: String): List<RemoteCatalogItem> {
            return parsedCatalogByText[rawText]
                ?: parsedCatalog.takeIf { rawText == TEST_HTTP_RESPONSE }
                ?: error("unexpected raw text: $rawText")
        }
    }

    private class FakeCatalogSource(
        /** 兜底目录结果。 */
        private val catalog: List<RemoteCatalogItem>,
    ) : AppCatalogSource {
        override suspend fun load(): List<RemoteCatalogItem> = catalog
    }

    private class FakeHttpClient(
        /** 固定返回的目录文本。 */
        private val responseText: String,
    ) : AppCatalogHttpClient {
        override suspend fun fetch(endpointUrl: String): String = responseText
    }

    private class FailingHttpClient : AppCatalogHttpClient {
        override suspend fun fetch(endpointUrl: String): String = error("http unavailable")
    }

    private class QuietLogger : AppLogger() {
        override fun d(tag: String, message: String) = Unit
    }

    private companion object {
        /** HTTP 返回的目录文本。 */
        const val TEST_HTTP_RESPONSE = "remote-catalog"

        /** 缓存中的目录文本。 */
        const val TEST_CACHE_RESPONSE = "cache-catalog"

        /** HTTP 目录项。 */
        val TEST_ITEM = createItem("remote.app", "Remote App")

        /** 缓存目录项。 */
        val CACHE_ITEM = createItem("cache.app", "Cache App")

        /** 资源兜底目录项。 */
        val FALLBACK_ITEM = createItem("fallback.app", "Fallback App")

        /** 构造测试目录项。 */
        fun createItem(appId: String, name: String): RemoteCatalogItem {
            return RemoteCatalogItem(
                appId = appId,
                appInfo = AppInfo(
                    appId = appId,
                    packageName = "com.nio.$appId",
                    name = name,
                    description = "$name description",
                    versionName = "1.0.0",
                ),
                appDetail = AppDetail(
                    appId = appId,
                    packageName = "com.nio.$appId",
                    name = name,
                    description = "$name detail",
                    versionName = "1.0.0",
                    apkUrl = "",
                ),
                upgradeInfo = UpgradeInfo(
                    appId = appId,
                    latestVersion = "1.0.1",
                    apkUrl = "",
                    hasUpgrade = true,
                ),
            )
        }
    }
}
