package com.xzq.appstore.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xzq.appstore.data.datasource.local.AppLocalDataSource
import com.xzq.appstore.data.datasource.remote.AppCatalogHttpClient
import com.xzq.appstore.data.datasource.remote.AppCatalogHttpRequest
import com.xzq.appstore.data.datasource.remote.AppCatalogHttpResponse
import com.xzq.appstore.data.datasource.remote.AppRemoteDataSource
import com.xzq.appstore.data.datasource.remote.DownloadSourceCatalog
import com.xzq.appstore.data.datasource.system.AppSystemDataSource
import com.xzq.appstore.data.downloadenv.DownloadEnvironmentConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RealAppRepositoryTest {
    private lateinit var context: Context
    private lateinit var local: AppLocalDataSource
    private lateinit var repository: RealAppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.mkdirs()

        val catalogJson =
            """
            {
              "apps": [
                {
                  "appId": "test_app",
                  "packageName": "com.example.test",
                  "name": "测试应用",
                  "versionName": "1.0.0"
                }
              ]
            }
            """.trimIndent()

        val httpClient = StubCatalogHttpClient(catalogJson)
        val remote =
            AppRemoteDataSource(
                context = context,
                sourceCatalog = DownloadSourceCatalog(DownloadEnvironmentConfig()),
                catalogEndpointUrl = "https://catalog.example.com/catalog.json",
                httpClient = httpClient,
            )
        local = AppLocalDataSource(context)
        val system = AppSystemDataSource(context)
        repository = RealAppRepository(remote, local, system)
    }

    @Test
    fun `markInstalled persists InstalledApp built from remote detail`() =
        runTest {
            repository.markInstalled("test_app")

            val installed = repository.getInstalledApps().single()
            assertEquals("test_app", installed.appId)
            assertEquals("com.example.test", installed.packageName)
            assertEquals("测试应用", installed.name)
            assertEquals("1.0.0", installed.versionName)
        }

    @Test
    fun `markInstalled prefers staged upgrade version over remote detail`() =
        runTest {
            repository.stageUpgrade("test_app", "2.0.0")

            repository.markInstalled("test_app")

            val installed = repository.getInstalledApps().single()
            assertEquals("2.0.0", installed.versionName)
        }

    @Test
    fun `isInstalled flips from false to true after markInstalled`() =
        runTest {
            assertFalse(repository.isInstalled("test_app"))
            repository.markInstalled("test_app")
            assertTrue(repository.isInstalled("test_app"))
        }

    private class StubCatalogHttpClient(
        private val body: String,
    ) : AppCatalogHttpClient {
        override suspend fun fetch(request: AppCatalogHttpRequest): AppCatalogHttpResponse =
            AppCatalogHttpResponse(statusCode = 200, body = body)
    }
}
