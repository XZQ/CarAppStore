package com.xzq.appstore.domain.upgrade

import com.xzq.appstore.core.installer.InstallEvent
import com.xzq.appstore.core.installer.InstallFailureCode
import com.xzq.appstore.core.installer.InstallRequest
import com.xzq.appstore.core.installer.PackageInstaller
import com.xzq.appstore.core.logger.AppLogger
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.DownloadPreferences
import com.xzq.appstore.data.model.DownloadSegmentRecord
import com.xzq.appstore.data.model.InstalledApp
import com.xzq.appstore.data.model.UpgradeInfo
import com.xzq.appstore.data.repository.AppRepository
import com.xzq.appstore.domain.download.DefaultDownloadManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.DefaultInstallManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.policy.PolicyResult
import com.xzq.appstore.domain.state.DefaultStateCenter
import com.xzq.appstore.domain.state.DownloadStatus
import com.xzq.appstore.domain.state.InstallStatus
import com.xzq.appstore.domain.state.UpgradeStatus
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DefaultUpgradeManagerTest {

    private lateinit var workDir: File
    private lateinit var stateCenter: DefaultStateCenter
    private lateinit var repository: FakeUpgradeRepository

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("upgrade-manager-test").toFile()
        stateCenter = DefaultStateCenter()
        repository = FakeUpgradeRepository(workDir)
    }

    @Test
    fun `checkUpgrade 无已安装版本时返回 false`() = runBlocking {
        val manager = createManager()
        val result = manager.checkUpgrade(TEST_APP_ID)
        assertEquals(false, result)
        assertEquals(UpgradeStatus.NONE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `checkUpgrade 有新版本时返回 true 并标记 AVAILABLE`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0")
        val manager = createManager()
        val result = manager.checkUpgrade(TEST_APP_ID)
        assertEquals(true, result)
        assertEquals(UpgradeStatus.AVAILABLE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `checkUpgrade 版本相同时返回 false`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "2.0.0")
        val manager = createManager()
        val result = manager.checkUpgrade(TEST_APP_ID)
        assertEquals(false, result)
        assertEquals(UpgradeStatus.NONE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `startUpgrade 策略拦截时直接标记升级失败`() = runBlocking {
        val manager = createManager(policyCenter = DenyAllPolicyCenter())
        manager.startUpgrade(TEST_APP_ID)
        assertEquals(UpgradeStatus.FAILED, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `startUpgrade 版本无升级时标记 NONE`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "2.0.0")
        val manager = createManager()
        manager.startUpgrade(TEST_APP_ID)
        assertEquals(UpgradeStatus.NONE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `startUpgrade 成功完成下载和安装后升级状态回到 NONE`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0")
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)

        val manager = createManager()
        manager.startUpgrade(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(InstallStatus.INSTALLED, state.installStatus)
        assertEquals(DownloadStatus.COMPLETED, state.downloadStatus)
    }

    @Test
    fun `startUpgrade stageUpgrade 记录了目标版本`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0")
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)

        val manager = createManager()
        manager.startUpgrade(TEST_APP_ID)

        assertEquals("2.0.0", repository.stagedVersion)
    }

    @Test
    fun `checkAllUpgrades 返回所有有升级可用的应用`() = runBlocking {
        stateCenter.syncInstalled("app.a", "1.0.0")
        stateCenter.syncInstalled("app.b", "1.0.0")
        stateCenter.syncInstalled("app.c", "2.0.0")
        repository.addInstalledApp("app.a", "App A", "1.0.0")
        repository.addInstalledApp("app.b", "App B", "1.0.0")
        repository.addInstalledApp("app.c", "App C", "2.0.0")

        val manager = createManager()
        val result = manager.checkAllUpgrades()

        assertEquals(listOf("app.a", "app.b"), result)
        assertEquals(UpgradeStatus.AVAILABLE, stateCenter.snapshot("app.a").upgradeStatus)
        assertEquals(UpgradeStatus.AVAILABLE, stateCenter.snapshot("app.b").upgradeStatus)
        assertEquals(UpgradeStatus.NONE, stateCenter.snapshot("app.c").upgradeStatus)
    }

    @Test
    fun `startBatchUpgrade 逐个串行执行升级`() = runBlocking {
        stateCenter.syncInstalled("app.a", "1.0.0")
        stateCenter.syncInstalled("app.b", "1.0.0")
        repository.addInstalledApp("app.a", "App A", "1.0.0")
        repository.addInstalledApp("app.b", "App B", "1.0.0")

        val apkA = File(workDir, "app.a.apk").apply { writeBytes(ByteArray(512)) }
        val apkB = File(workDir, "app.b.apk").apply { writeBytes(ByteArray(512)) }
        repository.saveApk("app.a", apkA.absolutePath)
        repository.saveApk("app.b", apkB.absolutePath)

        val manager = createManager()
        manager.startBatchUpgrade(listOf("app.a", "app.b"))

        assertEquals(InstallStatus.INSTALLED, stateCenter.snapshot("app.a").installStatus)
        assertEquals(InstallStatus.INSTALLED, stateCenter.snapshot("app.b").installStatus)
    }

    @Test
    fun `startUpgrade 下载被取消时不会无限等待并标记升级失败`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0")
        val manager = createManager(
            fileDownloader = object : com.xzq.appstore.core.downloader.FileDownloader {
                override suspend fun download(
                    request: com.xzq.appstore.core.downloader.DownloadRequest,
                    control: com.xzq.appstore.core.downloader.DownloadExecutionControl,
                    onEvent: suspend (com.xzq.appstore.core.downloader.DownloadEvent) -> Unit,
                ) {
                    onEvent(
                        com.xzq.appstore.core.downloader.DownloadEvent.Stopped(
                            reason = com.xzq.appstore.core.downloader.DownloadStopReason.CANCELED,
                            downloadedBytes = 0L,
                            totalBytes = 1024L,
                        )
                    )
                }
            }
        )

        withTimeout(1_000L) {
            manager.startUpgrade(TEST_APP_ID)
        }

        assertEquals(DownloadStatus.CANCELED, stateCenter.snapshot(TEST_APP_ID).downloadStatus)
        assertEquals(UpgradeStatus.FAILED, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkUpgrade 空白 appId 抛异常`() {
        runBlocking {
            val manager = createManager()
            manager.checkUpgrade("")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startUpgrade 空白 appId 抛异常`() {
        runBlocking {
            val manager = createManager()
            manager.startUpgrade("")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startBatchUpgrade 空列表抛异常`() {
        runBlocking {
            val manager = createManager()
            manager.startBatchUpgrade(emptyList())
        }
    }

    private fun createManager(
        policyCenter: PolicyCenter = AllowAllPolicyCenter(),
        fileDownloader: com.xzq.appstore.core.downloader.FileDownloader? = null,
        packageInstaller: PackageInstaller? = null,
    ): DefaultUpgradeManager {
        File(workDir, "test.apk").apply { parentFile?.mkdirs() }
        val effectivePackageInstaller = packageInstaller ?: object : com.xzq.appstore.core.installer.PackageInstaller {
            override suspend fun install(
                request: InstallRequest,
                onEvent: suspend (InstallEvent) -> Unit,
            ) {
                onEvent(InstallEvent.Waiting)
                onEvent(InstallEvent.SessionCreated(1))
                onEvent(InstallEvent.Installing)
                onEvent(InstallEvent.Success("2.0.0"))
            }
        }

        val effectiveFileDownloader = fileDownloader ?: object : com.xzq.appstore.core.downloader.FileDownloader {
            override suspend fun download(
                request: com.xzq.appstore.core.downloader.DownloadRequest,
                control: com.xzq.appstore.core.downloader.DownloadExecutionControl,
                onEvent: suspend (com.xzq.appstore.core.downloader.DownloadEvent) -> Unit,
            ) {
                request.targetFile.apply { parentFile?.mkdirs(); writeBytes(ByteArray(1024)) }
                onEvent(com.xzq.appstore.core.downloader.DownloadEvent.MetaReady(
                    com.xzq.appstore.core.downloader.DownloadRemoteMeta(contentLength = 1024L, supportsRange = false)
                ))
                onEvent(com.xzq.appstore.core.downloader.DownloadEvent.Running(1024L, 1024L, 128L))
                onEvent(com.xzq.appstore.core.downloader.DownloadEvent.Completed(request.targetFile, 1024L))
            }
        }

        val downloadManager = DefaultDownloadManager(
            repository = repository,
            stateCenter = stateCenter,
            policyCenter = policyCenter,
            fileDownloader = effectiveFileDownloader,
            logger = QuietLogger(),
            tracker = QuietTracker(),
        )

        val installManager = DefaultInstallManager(
            repository = repository,
            stateCenter = stateCenter,
            policyCenter = policyCenter,
            packageInstaller = effectivePackageInstaller,
            logger = QuietLogger(),
            tracker = QuietTracker(),
        )

        return DefaultUpgradeManager(
            repository = repository,
            stateCenter = stateCenter,
            policyCenter = policyCenter,
            downloadManager = downloadManager,
            installManager = installManager,
            logger = QuietLogger(),
            tracker = QuietTracker(),
        )
    }

    private companion object {
        const val TEST_APP_ID = "test.app"
    }

    private class AllowAllPolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(com.xzq.appstore.data.model.PolicySettings())
        override fun canDownload(appId: String) = PolicyResult(true)
        override fun canInstall(appId: String) = PolicyResult(true)
        override fun canUpgrade(appId: String) = PolicyResult(true)
        override fun observeSettings() = settingsFlow
        override fun getSettings() = settingsFlow.value
        override fun getStoredSettings() = settingsFlow.value
        override fun updateSettings(settings: com.xzq.appstore.data.model.PolicySettings) {
            settingsFlow.value = settings
        }
    }

    private class DenyAllPolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(com.xzq.appstore.data.model.PolicySettings())
        override fun canDownload(appId: String) = PolicyResult(false, "禁止")
        override fun canInstall(appId: String) = PolicyResult(false, "禁止")
        override fun canUpgrade(appId: String) = PolicyResult(false, "禁止")
        override fun observeSettings() = settingsFlow
        override fun getSettings() = settingsFlow.value
        override fun getStoredSettings() = settingsFlow.value
        override fun updateSettings(settings: com.xzq.appstore.data.model.PolicySettings) {
            settingsFlow.value = settings
        }
    }

    private class QuietLogger : AppLogger() {
        override fun d(tag: String, message: String) = Unit
    }

    private class QuietTracker : EventTracker() {
        override fun track(event: String) = Unit
    }

    private class FakeUpgradeRepository(private val workDir: File) : AppRepository {
        private val apkPaths = mutableMapOf<String, String>()
        var stagedVersion: String? = null
        private val installedAppsList = mutableListOf<InstalledApp>()

        fun saveApk(appId: String, path: String) { apkPaths[appId] = path }
        fun addInstalledApp(appId: String, name: String, versionName: String) {
            installedAppsList.add(InstalledApp(appId = appId, packageName = "com.nio.$appId", name = name, versionName = versionName))
        }

        override suspend fun getHomeApps() = emptyList<com.xzq.appstore.data.model.AppInfo>()
        override suspend fun getAppDetail(appId: String) = AppDetail(
            appId = appId, packageName = "com.nio.$appId", name = "App $appId",
            description = "", versionName = "1.0.0", apkUrl = "https://example.com/$appId.apk",
        )
        override suspend fun getInstalledApps() = if (installedAppsList.isNotEmpty()) installedAppsList.toList() else listOf(InstalledApp(appId = TEST_APP_ID, packageName = "com.nio.test", name = "Test", versionName = "1.0.0"))
        override suspend fun markInstalled(appId: String) = Unit
        override suspend fun isInstalled(appId: String) = true
        override suspend fun saveDownloadedApk(appId: String, apkPath: String) { apkPaths[appId] = apkPath }
        override suspend fun getDownloadedApk(appId: String) = apkPaths[appId]
        override suspend fun clearDownloadedApk(appId: String) { apkPaths.remove(appId) }
        override suspend fun getUpgradeInfo(appId: String) = UpgradeInfo(appId = appId, latestVersion = "2.0.0", apkUrl = "", hasUpgrade = true)
        override suspend fun stageUpgrade(appId: String, versionName: String) { stagedVersion = versionName }
        override suspend fun peekStagedUpgradeVersion(appId: String) = stagedVersion
        override suspend fun saveDownloadTask(record: com.xzq.appstore.data.model.DownloadTaskRecord) = Unit
        override suspend fun getDownloadTask(appId: String): com.xzq.appstore.data.model.DownloadTaskRecord? = null
        override suspend fun getAllDownloadTasks() = emptyList<com.xzq.appstore.data.model.DownloadTaskRecord>()
        override suspend fun removeDownloadTask(appId: String) = Unit
        override suspend fun clearCompletedDownloadTasks() = 0
        override suspend fun saveDownloadSegments(appId: String, segments: List<com.xzq.appstore.data.model.DownloadSegmentRecord>) = Unit
        override suspend fun getDownloadSegments(appId: String) = emptyList<com.xzq.appstore.data.model.DownloadSegmentRecord>()
        override suspend fun getOrCreateDownloadFile(appId: String): File {
            return File(workDir, "downloads/$appId.apk").apply { parentFile?.mkdirs() }
        }
        override suspend fun getDownloadPreferences() = DownloadPreferences()
        override suspend fun saveDownloadPreferences(preferences: com.xzq.appstore.data.model.DownloadPreferences) = Unit
        override fun getPolicySettings() = com.xzq.appstore.data.model.PolicySettings()
        override fun openApp(packageName: String) = false
    }
}
