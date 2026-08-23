package com.xzq.appstore.data.datasource.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xzq.appstore.data.downloadenv.DownloadEnvironmentConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
                  "versionName": "1.0.0"
                }
              ]
            }
        """.trimIndent()
    }
}
