package com.xzq.appstore.data.datasource.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xzq.appstore.common.grayscale.GrayscaleHeaderStore
import com.xzq.appstore.data.downloadenv.DownloadEnvironmentConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppRemoteDataSourceTest {
    @Test
    fun `单调时钟回退时目录缓存会失效并重新加载`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpClient = SequencedCatalogHttpClient(
            listOf(catalogJson("Catalog A"), catalogJson("Catalog B")),
        )
        var nowNanos = 1_000_000_000L
        val source = AppRemoteDataSource(
            context = context,
            sourceCatalog = DownloadSourceCatalog(DownloadEnvironmentConfig()),
            catalogEndpointUrl = "https://catalog.example.com/catalog.json",
            httpClient = httpClient,
            monotonicClockNanos = { nowNanos },
        )

        assertEquals("Catalog A", source.getHomeApps().single().name)
        nowNanos -= 1L
        assertEquals("Catalog B", source.getHomeApps().single().name)
        assertEquals(2, httpClient.fetchCount)
    }

    @Test
    fun `灰度分组切换后立即失效目录内存缓存`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpClient = SequencedCatalogHttpClient(
            listOf(catalogJson("Catalog A"), catalogJson("Catalog B")),
        )
        val source = AppRemoteDataSource(
            context = context,
            sourceCatalog = DownloadSourceCatalog(DownloadEnvironmentConfig()),
            catalogEndpointUrl = "https://catalog.example.com/catalog.json",
            httpClient = httpClient,
            monotonicClockNanos = { 1_000_000_000L },
        )
        GrayscaleHeaderStore.save(context, enabled = true, tag = "group-a")
        assertEquals("Catalog A", source.getHomeApps().single().name)

        // 同一时钟、同一 TTL 窗口内切换灰度分组：必须重新请求，不能复用旧目录。
        GrayscaleHeaderStore.save(context, enabled = true, tag = "group-b")
        assertEquals("Catalog B", source.getHomeApps().single().name)
        assertEquals(2, httpClient.fetchCount)
    }

    @Test
    fun `失败回退目录只做短期合流而不是固定缓存 30 秒`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpClient = FlakyCatalogHttpClient(failures = 1, body = catalogJson("Catalog B"))
        var nowNanos = 1_000_000_000L
        val source = AppRemoteDataSource(
            context = context,
            sourceCatalog = DownloadSourceCatalog(DownloadEnvironmentConfig()),
            catalogEndpointUrl = "https://catalog.example.com/catalog.json",
            httpClient = httpClient,
            monotonicClockNanos = { nowNanos },
        )

        source.getHomeApps()
        assertEquals(1, httpClient.fetchCount)

        // 降级结果在 3s 内仍可用于合并同一屏的并发查询。
        nowNanos += 1_000_000_000L
        source.getHomeApps()
        assertEquals(1, httpClient.fetchCount)

        // 超过降级 TTL 后网络已恢复：必须重新请求远端，而不是继续返回兜底目录。
        nowNanos += 3_000_000_000L
        assertEquals("Catalog B", source.getHomeApps().single().name)
        assertEquals(2, httpClient.fetchCount)
    }

    private class FlakyCatalogHttpClient(
        private val failures: Int,
        private val body: String,
    ) : AppCatalogHttpClient {
        /** 实际发起的目录请求次数。 */
        var fetchCount: Int = 0
            private set

        override suspend fun fetch(request: AppCatalogHttpRequest): AppCatalogHttpResponse {
            fetchCount += 1
            if (fetchCount <= failures) {
                throw IOException("catalog endpoint unavailable")
            }
            return AppCatalogHttpResponse(statusCode = 200, body = body)
        }
    }

    private class SequencedCatalogHttpClient(bodies: List<String>) : AppCatalogHttpClient {
        private val responseBodies = bodies.iterator()

        /** 实际发起的目录请求次数。 */
        var fetchCount: Int = 0
            private set

        override suspend fun fetch(request: AppCatalogHttpRequest): AppCatalogHttpResponse {
            fetchCount += 1
            return AppCatalogHttpResponse(statusCode = 200, body = responseBodies.next())
        }
    }

    private companion object {
        fun catalogJson(name: String): String = """
            {
              "apps": [
                {
                  "appId": "test_app",
                  "packageName": "com.example.test",
                  "name": "$name",
                  "versionName": "1.0.0",
                  "versionCode": 1,
                  "signerCertificateSha256": ["AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"]
                }
              ]
            }
        """.trimIndent()
    }
}
