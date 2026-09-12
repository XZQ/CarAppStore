package com.xzq.appstore.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
import org.robolectric.Shadows.shadowOf
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

        val catalogJson = """
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
        val remote = AppRemoteDataSource(
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
    fun `absence requires full package visibility`() = runTest {
        shadowOf(context as android.app.Application).denyPermissions("android.permission.QUERY_ALL_PACKAGES")
        assertTrue(repository.getInstalledAppsSnapshot().confirmedAbsentAppIds.isEmpty())
        shadowOf(context as android.app.Application).grantPermissions("android.permission.QUERY_ALL_PACKAGES")
        assertEquals(setOf("test_app"), repository.getInstalledAppsSnapshot().confirmedAbsentAppIds)
    }

    @Test
    fun `system uninstall is confirmed even for an application missing from catalog`() = runTest {
        local.saveInstalledApp(com.xzq.appstore.data.model.InstalledApp("removed.app", "com.example.removed", "Removed", "1.0"))
        shadowOf(context as android.app.Application).grantPermissions("android.permission.QUERY_ALL_PACKAGES")
        assertTrue("removed.app" in repository.getInstalledAppsSnapshot().confirmedAbsentAppIds)
    }

    @Test
    fun `application omitted by catalog stays installed when system still has it`() = runTest {
        local.saveInstalledApp(com.xzq.appstore.data.model.InstalledApp("removed.app", "com.example.removed", "Removed", "1.0"))
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.example.removed"
            versionName = "1.0"
            applicationInfo = ApplicationInfo().apply { packageName = "com.example.removed" }
        })
        assertTrue(repository.getInstalledAppsSnapshot().apps.any { it.appId == "removed.app" })
    }

    @Test
    fun `markInstalled persists InstalledApp built from remote detail`() = runTest {
        installSystemPackage()
        repository.markInstalled("test_app")

        val installed = repository.getInstalledApps().single()
        assertEquals("test_app", installed.appId)
        assertEquals("com.example.test", installed.packageName)
        assertEquals("测试应用", installed.name)
        assertEquals("1.0.0", installed.versionName)
    }

    @Test
    fun `markInstalled prefers staged upgrade version over remote detail`() = runTest {
        installSystemPackage(versionName = "2.0.0")
        repository.stageUpgrade("test_app", "2.0.0")

        repository.markInstalled("test_app")

        val installed = repository.getInstalledApps().single()
        assertEquals("2.0.0", installed.versionName)
    }

    @Test
    fun `isInstalled flips from false to true after markInstalled`() = runTest {
        assertFalse(repository.isInstalled("test_app"))
        installSystemPackage()
        repository.markInstalled("test_app")
        assertTrue(repository.isInstalled("test_app"))
    }

    @Test
    fun `getInstalledApps ignores stale local mirror after system uninstall`() = runTest {
        installSystemPackage()
        repository.markInstalled("test_app")
        shadowOf(context.packageManager).removePackage("com.example.test")

        assertTrue(repository.getInstalledApps().isEmpty())
        assertFalse(repository.isInstalled("test_app"))
    }

    private fun installSystemPackage(versionName: String = "1.0.0") {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = "com.example.test"
                this.versionName = versionName
                applicationInfo = ApplicationInfo().apply { packageName = "com.example.test" }
            },
        )
    }

    private class StubCatalogHttpClient(private val body: String) : AppCatalogHttpClient {
        override suspend fun fetch(request: AppCatalogHttpRequest): AppCatalogHttpResponse = AppCatalogHttpResponse(statusCode = 200, body = body)
    }
}
