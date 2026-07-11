package com.xzq.appstore.data.datasource.system

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AppSystemDataSource 直接调用 PackageManager，是 data 层最依赖 Android 系统能力的类。
 * 用 Robolectric 验证：
 * 1. 未安装包的查询链路（isPackageInstalled / getInstalledVersion / queryInstalledApps）
都能正确返回空结果而不抛出 NameNotFoundException；
 * 2. 不存在的 APK 路径返回 null；
 * 3. openApp 对未注册包名返回 false。
 *
 * 正向用例通过 Robolectric 的 ShadowPackageManager 注册 PackageInfo 来覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppSystemDataSourceTest {
    private lateinit var context: Context
    private lateinit var dataSource: AppSystemDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataSource = AppSystemDataSource(context)
    }

    @Test
    fun `isPackageInstalled returns false for unknown package`() {
        assertFalse(dataSource.isPackageInstalled("com.example.nonexistent"))
    }

    @Test
    fun `getInstalledVersion returns null for unknown package`() {
        assertNull(dataSource.getInstalledVersion("com.example.nonexistent"))
    }

    @Test
    fun `queryInstalledApps returns empty list when input is empty`() {
        val result = dataSource.queryInstalledApps(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryInstalledApps filters out unknown packages`() {
        val result = dataSource.queryInstalledApps(setOf("com.example.nonexistent"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPackageInfoFromApk returns null when apk path does not exist`() {
        val result = dataSource.getPackageInfoFromApk("/definitely/not/a/real/path.apk")
        assertNull(result)
    }

    @Test
    fun `openApp returns false when no launch intent available`() {
        // 未注册包名 -> getLaunchIntentForPackage 返回 null -> 直接 false。
        assertFalse(dataSource.openApp("com.example.nonexistent"))
    }

    @Test
    fun `isPackageInstalled returns true after shadow install`() {
        installFakePackage(packageName = "com.fake.app", versionName = "1.2.3")

        assertTrue(dataSource.isPackageInstalled("com.fake.app"))
    }

    @Test
    fun `getInstalledVersion returns versionName after shadow install`() {
        installFakePackage(packageName = "com.fake.app", versionName = "2.0.0")

        assertEquals("2.0.0", dataSource.getInstalledVersion("com.fake.app"))
    }

    @Test
    fun `queryInstalledApps returns installed entries for known packages`() {
        installFakePackage(packageName = "com.fake.app", versionName = "1.0")

        val result = dataSource.queryInstalledApps(setOf("com.fake.app", "com.example.unknown"))
        assertEquals(1, result.size)
        val installed = result.first()
        assertEquals("com.fake.app", installed.appId)
        assertEquals("com.fake.app", installed.packageName)
        assertEquals("1.0", installed.versionName)
    }

    private fun installFakePackage(packageName: String, versionName: String) {
        val packageInfo = PackageInfo().apply {
            this.packageName = packageName
            this.versionName = versionName
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                this.packageName = packageName
                nonLocalizedLabel = packageName
            }
        }
        shadowOf(context.packageManager).installPackage(packageInfo)
    }
}
