package com.nio.appstore.domain.appmanager

import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadSegmentRecord
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.data.model.InstalledApp
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.data.model.UpgradeInfo
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.policy.PolicyResult
import com.nio.appstore.domain.state.DefaultStateCenter
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.UpgradeStatus
import com.nio.appstore.domain.text.BusinessText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DefaultAppManagerTest {
    /** 当前测试专用工作目录。 */
    private lateinit var workDir: File

    /** 聚合层依赖的状态中心。 */
    private lateinit var stateCenter: DefaultStateCenter

    /** 聚合层依赖的仓库替身。 */
    private lateinit var repository: FakeAppManagerRepository

    /** 聚合层依赖的策略中心。 */
    private lateinit var policyCenter: FakePolicyCenter

    /** 安装会话存储。 */
    private lateinit var installSessionStore: InstallSessionStore

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("app-manager-test").toFile()
        stateCenter = DefaultStateCenter()
        repository = FakeAppManagerRepository(workDir)
        policyCenter = FakePolicyCenter()
        installSessionStore = InstallSessionStore(File(workDir, "install_sessions.json"))
    }

    @Test
    fun `searchApps 支持分类 标签 推荐理由 和关键词召回`() = runBlocking {
        val manager = createManager()

        assertEquals(listOf("nav.app"), manager.searchApps("导航").map { it.appId })
        assertEquals(listOf("music.app"), manager.searchApps("编辑精选").map { it.appId })
        assertEquals(listOf("podcast.app"), manager.searchApps("长途陪伴").map { it.appId })
        assertEquals(listOf("podcast.app"), manager.searchApps("播客").map { it.appId })
    }

    @Test
    fun `getMyApps 返回已安装应用和有任务的应用`() = runBlocking {
        repository.installedApps += InstalledApp(
            appId = "music.app",
            packageName = "com.nio.music",
            name = "车载音乐",
            versionName = "1.0.0",
        )
        stateCenter.updateDownload(
            appId = "nav.app",
            status = DownloadStatus.RUNNING,
            progress = 42,
            localApkPath = null,
            errorMessage = null,
            errorCode = null,
        )

        val manager = createManager()
        val result = manager.getMyApps()

        assertEquals(setOf("nav.app", "music.app"), result.map { it.appId }.toSet())
        assertTrue(result.any { it.appId == "nav.app" && it.primaryAction == PrimaryAction.PAUSE })
        assertTrue(result.any { it.appId == "music.app" && it.primaryAction == PrimaryAction.UPGRADE })
    }

    @Test
    fun `getUpgradeTasks 只返回可升级应用并生成正确统计`() = runBlocking {
        repository.installedApps += InstalledApp(
            appId = "music.app",
            packageName = "com.nio.music",
            name = "车载音乐",
            versionName = "1.0.0",
        )
        repository.installedApps += InstalledApp(
            appId = "podcast.app",
            packageName = "com.nio.podcast",
            name = "车载播客",
            versionName = "2.0.0",
        )

        val manager = createManager()
        val tasks = manager.getUpgradeTasks()
        val stats = manager.getUpgradeTaskStats()

        assertEquals(listOf("music.app"), tasks.map { it.appId })
        assertEquals(UpgradeStatus.AVAILABLE, stateCenter.snapshot("music.app").upgradeStatus)
        assertEquals(1, stats.pendingCount)
        assertEquals(0, stats.activeCount)
        assertEquals(0, stats.failedCount)
    }

    @Test
    fun `getPolicyPrompt 根据当前策略设置聚合提示文案`() {
        policyCenter.settingsFlow.value = PolicySettings(
            wifiConnected = false,
            parkingMode = false,
            lowStorageMode = true,
        )
        val manager = createManager()

        val result = manager.getPolicyPrompt()

        assertEquals(
            listOf(
                BusinessText.POLICY_DOWNLOAD_CELLULAR,
                BusinessText.POLICY_INSTALL_DRIVING,
                BusinessText.POLICY_STORAGE_LIMITED,
            ).joinToString("；"),
            result,
        )
    }

    /** 创建待测聚合层实例。 */
    private fun createManager(): DefaultAppManager {
        return DefaultAppManager(
            repository = repository,
            stateCenter = stateCenter,
            installSessionStore = installSessionStore,
            policyCenter = policyCenter,
        )
    }

    /** 仅覆盖聚合层测试场景的仓库替身。 */
    private class FakeAppManagerRepository(
        /** 当前测试使用的工作目录。 */
        private val workDir: File,
    ) : AppRepository {
        /** 首页目录数据。 */
        private val homeApps = listOf(
            AppInfo(
                appId = "nav.app",
                packageName = "com.nio.nav",
                name = "车载导航",
                description = "适合日常通勤",
                versionName = "2.0.0",
                category = "导航",
                editorialTag = "高频刚需",
                recommendedReason = "覆盖城市通勤",
                searchKeywords = listOf("地图", "出行"),
            ),
            AppInfo(
                appId = "music.app",
                packageName = "com.nio.music",
                name = "车载音乐",
                description = "随车畅听",
                versionName = "1.1.0",
                category = "音乐",
                editorialTag = "编辑精选",
                recommendedReason = "收藏歌单同步",
                searchKeywords = listOf("听歌", "电台"),
            ),
            AppInfo(
                appId = "podcast.app",
                packageName = "com.nio.podcast",
                name = "车载播客",
                description = "陪伴你的长途时光",
                versionName = "2.0.0",
                category = "有声",
                editorialTag = "长途推荐",
                recommendedReason = "长途陪伴",
                searchKeywords = listOf("播客", "有声书"),
            ),
        )

        /** 已安装应用列表。 */
        val installedApps = mutableListOf<InstalledApp>()

        /** 下载偏好设置。 */
        private var downloadPreferences = DownloadPreferences()

        override suspend fun getHomeApps(): List<AppInfo> = homeApps

        override suspend fun getAppDetail(appId: String): AppDetail {
            val app = requireNotNull(homeApps.firstOrNull { it.appId == appId })
            return AppDetail(
                appId = app.appId,
                packageName = app.packageName,
                name = app.name,
                description = app.description,
                versionName = app.versionName,
                apkUrl = "https://example.com/${app.appId}.apk",
            )
        }

        override suspend fun getInstalledApps(): List<InstalledApp> = installedApps.toList()

        override suspend fun markInstalled(appId: String) = Unit

        override suspend fun isInstalled(appId: String): Boolean {
            return installedApps.any { it.appId == appId }
        }

        override suspend fun saveDownloadedApk(appId: String, apkPath: String) = Unit

        override suspend fun getDownloadedApk(appId: String): String? = null

        override suspend fun clearDownloadedApk(appId: String) = Unit

        override suspend fun getUpgradeInfo(appId: String): UpgradeInfo {
            return when (appId) {
                "music.app" -> UpgradeInfo(
                    appId = appId,
                    latestVersion = "1.1.0",
                    apkUrl = "https://example.com/$appId.apk",
                    hasUpgrade = true,
                )
                "podcast.app" -> UpgradeInfo(
                    appId = appId,
                    latestVersion = "2.0.0",
                    apkUrl = "https://example.com/$appId.apk",
                    hasUpgrade = false,
                )
                else -> UpgradeInfo(
                    appId = appId,
                    latestVersion = "2.0.0",
                    apkUrl = "https://example.com/$appId.apk",
                    hasUpgrade = true,
                )
            }
        }

        override suspend fun stageUpgrade(appId: String, versionName: String) = Unit

        override suspend fun peekStagedUpgradeVersion(appId: String): String? = null

        override suspend fun saveDownloadTask(record: DownloadTaskRecord) = Unit

        override suspend fun getDownloadTask(appId: String): DownloadTaskRecord? = null

        override suspend fun getAllDownloadTasks(): List<DownloadTaskRecord> = emptyList()

        override suspend fun removeDownloadTask(appId: String) = Unit

        override suspend fun clearCompletedDownloadTasks(): Int = 0

        override suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) = Unit

        override suspend fun getDownloadSegments(appId: String): List<DownloadSegmentRecord> = emptyList()

        override suspend fun getOrCreateDownloadFile(appId: String): File {
            return File(workDir, "$appId.apk")
        }

        override suspend fun getDownloadPreferences(): DownloadPreferences = downloadPreferences

        override suspend fun saveDownloadPreferences(preferences: DownloadPreferences) {
            downloadPreferences = preferences
        }

        override fun getPolicySettings(): PolicySettings = PolicySettings()

        override fun openApp(packageName: String): Boolean = true
    }

    /** 仅覆盖聚合层测试场景的策略中心替身。 */
    private class FakePolicyCenter : PolicyCenter {
        /** 当前测试策略流。 */
        val settingsFlow = MutableStateFlow(PolicySettings())

        override fun canDownload(appId: String): PolicyResult = PolicyResult(true)

        override fun canInstall(appId: String): PolicyResult = PolicyResult(true)

        override fun canUpgrade(appId: String): PolicyResult = PolicyResult(true)

        override fun observeSettings() = settingsFlow

        override fun getSettings(): PolicySettings = settingsFlow.value

        override fun getStoredSettings(): PolicySettings = settingsFlow.value

        override fun updateSettings(settings: PolicySettings) {
            settingsFlow.value = settings
        }
    }
}
