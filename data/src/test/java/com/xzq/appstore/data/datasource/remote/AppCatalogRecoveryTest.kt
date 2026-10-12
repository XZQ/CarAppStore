package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.common.grayscale.GrayscaleHeaderStore
import com.xzq.appstore.core.logger.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AppCatalogRecoveryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val cacheFile get() = File(temporaryFolder.root, "catalog.json")
    private val metadataFile get() = File(temporaryFolder.root, "catalog.meta.json")
    private val remoteCatalog = AppCatalogJsonParser.parse(catalogJson("Remote"))
    private val fallbackCatalog = AppCatalogJsonParser.parse(catalogJson("Fallback"))

    @Test
    fun `corrupted metadata does not prevent an unconditional network request`() = runBlocking {
        metadataFile.writeText("{broken metadata")
        val client = RecordingClient { response() }

        assertEquals(remoteCatalog, source(client).load())
        assertEquals(1, client.requests.size)
        assertNull(client.requests.single().eTag)
        assertEquals("etag-v1", AppCatalogCacheMetadataStore.read(metadataFile)?.eTag)
    }

    @Test
    fun `missing body never sends validators and unexpected 304 retries once unconditionally`() = runBlocking {
        source(RecordingClient { response() }).load()
        cacheFile.delete()
        val client = RecordingClient { index ->
            if (index == 0) AppCatalogHttpResponse(statusCode = 304, notModified = true) else response()
        }

        assertEquals(remoteCatalog, source(client).load())
        assertEquals(2, client.requests.size)
        client.requests.forEach {
            assertNull(it.eTag)
            assertNull(it.lastModified)
        }
    }

    @Test
    fun `repeated 304 without a body is bounded and falls back to resources`() = runBlocking {
        val client = RecordingClient { AppCatalogHttpResponse(statusCode = 304, notModified = true) }

        assertEquals(fallbackCatalog, source(client).load())
        assertEquals(2, client.requests.size)
    }

    @Test
    fun `corrupted or mismatched body invalidates conditional requests`() = runBlocking {
        source(RecordingClient { response() }).load()
        cacheFile.writeText(catalogJson("Different body"))
        val client = RecordingClient { response() }

        assertEquals(remoteCatalog, source(client).load())
        assertNull(client.requests.single().eTag)
    }

    @Test
    fun `malformed successful response preserves the last valid catalog`() = runBlocking {
        source(RecordingClient { response() }).load()
        val previousBody = cacheFile.readText()
        listOf("{}", "{\"error\":\"temporary\"}", "{\"apps\":null}", "{\"apps\":{}}").forEach { invalid ->
            assertEquals(remoteCatalog, source(RecordingClient { response(invalid) }).load())
            assertEquals(previousBody, cacheFile.readText())
        }
    }

    @Test
    fun `explicit empty catalog replaces old contents and remains a valid offline cache`() = runBlocking {
        source(RecordingClient { response() }).load()
        assertEquals(emptyList<RemoteCatalogItem>(), source(RecordingClient { response("{\"apps\":[]}") }).load())
        assertEquals(emptyList<RemoteCatalogItem>(), source(RecordingClient { throw IOException("offline") }).load())
    }

    @Test
    fun `shared paths never reuse another environment or channel body or validators`() = runBlocking {
        val originalHeaders = mapOf("X-Client-Channel" to "test", "X-Client-Platform" to "android")
        source(RecordingClient { response() }, headers = originalHeaders).load()
        listOf(
            "https://production.example.com/catalog" to originalHeaders,
            ENDPOINT to originalHeaders + ("X-Client-Channel" to "prod"),
        ).forEach { (endpoint, headers) ->
            val client = RecordingClient { throw IOException("offline") }
            assertEquals(fallbackCatalog, source(client, endpoint, headers).load())
            assertNull(client.requests.single().eTag)
        }
    }

    @Test
    fun `changing live grayscale configuration invalidates cache scope`() = runBlocking {
        var tag = "group-a"
        val client = RecordingClient { index -> if (index == 0) response() else throw IOException("offline") }
        val source = source(client, grayscale = { GrayscaleHeaderStore.GrayscaleHeaderConfig(true, tag) })
        assertEquals(remoteCatalog, source.load())

        tag = "group-b"
        assertEquals(fallbackCatalog, source.load())
        assertEquals("group-b", client.requests.last().headers[GrayscaleHeaderStore.HEADER_NAME])
        assertNull(client.requests.last().eTag)
    }

    @Test
    fun `legacy cache without request identity is discarded and refreshes without validators`() = runBlocking {
        cacheFile.writeText(catalogJson("Legacy"))
        AppCatalogCacheMetadataStore.write(metadataFile, AppCatalogCacheMetadata(eTag = "legacy-etag"))
        val client = RecordingClient { response() }

        assertEquals(remoteCatalog, source(client).load())
        assertNull(client.requests.single().eTag)
    }

    @Test
    fun `valid 304 refreshes validators and preserves body identity for offline use`() = runBlocking {
        source(RecordingClient { response() }).load()
        val client = RecordingClient { AppCatalogHttpResponse(statusCode = 304, notModified = true, eTag = "etag-v2") }

        assertEquals(remoteCatalog, source(client).load())
        assertEquals("etag-v1", client.requests.single().eTag)
        assertEquals("etag-v2", AppCatalogCacheMetadataStore.read(metadataFile)?.eTag)
        assertEquals(remoteCatalog, source(RecordingClient { throw IOException("offline") }).load())
    }

    @Test
    fun `cache persistence failure still returns the successful response`() = runBlocking {
        cacheFile.mkdirs()
        assertEquals(remoteCatalog, source(RecordingClient { response() }).load())
    }

    @Test
    fun `cancellation is propagated instead of returning cached or fallback data`() {
        assertThrows(CancellationException::class.java) {
            runBlocking { source(RecordingClient { throw CancellationException("canceled") }).load() }
        }
        runBlocking { source(RecordingClient { response() }).load() }
        assertThrows(CancellationException::class.java) {
            runBlocking { source(RecordingClient { throw CancellationException("canceled") }).load() }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                source(RecordingClient { response() }, grayscale = { throw CancellationException("canceled") }).load()
            }
        }
    }

    @Test
    fun `metadata stores hashes instead of endpoint queries and configured secrets`() = runBlocking {
        val endpoint = "$ENDPOINT?signature=sensitive-query"
        val headers = mapOf("Authorization" to "Bearer sensitive-token", "X-Grayscale-Tag" to "sensitive-cohort")
        source(RecordingClient { response() }, endpoint, headers).load()

        val stored = metadataFile.readText()
        listOf(endpoint, "sensitive-query", "sensitive-token", "sensitive-cohort").forEach { assertFalse(stored.contains(it)) }
    }

    private fun source(
        client: AppCatalogHttpClient,
        endpoint: String = ENDPOINT,
        headers: Map<String, String> = emptyMap(),
        grayscale: () -> GrayscaleHeaderStore.GrayscaleHeaderConfig = { GrayscaleHeaderStore.GrayscaleHeaderConfig(false, "") },
    ): ResilientAppCatalogSource = ResilientAppCatalogSource(
        loader = object : AppCatalogLoader {
            override fun loadFromResource() = fallbackCatalog
            override fun parse(rawText: String) = AppCatalogJsonParser.parse(rawText)
        },
        endpointUrl = endpoint,
        requestHeaders = headers,
        httpClient = client,
        cacheFile = cacheFile,
        cacheMetadataFile = metadataFile,
        fallbackSource = object : AppCatalogSource { override suspend fun load() = fallbackCatalog },
        grayscaleHeaderProvider = grayscale,
        logger = object : AppLogger() { override fun d(tag: String, message: String) = Unit },
    )

    private class RecordingClient(private val response: (Int) -> AppCatalogHttpResponse) : AppCatalogHttpClient {
        val requests = mutableListOf<AppCatalogHttpRequest>()
        override suspend fun fetch(request: AppCatalogHttpRequest): AppCatalogHttpResponse {
            requests += request
            return response(requests.lastIndex)
        }
    }

    private fun response(body: String = catalogJson("Remote")) = AppCatalogHttpResponse(statusCode = 200, body = body, eTag = "etag-v1")

    private fun catalogJson(name: String): String = """
        {"apps":[{"appId":"test.app","packageName":"com.example.test","name":"$name","versionName":"1.0"}]}
    """.trimIndent()

    private companion object { const val ENDPOINT = "https://test.example.com/catalog" }
}
