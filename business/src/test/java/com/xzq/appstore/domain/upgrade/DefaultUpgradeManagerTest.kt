package com.xzq.appstore.domain.upgrade

import com.xzq.appstore.core.downloader.DownloadEvent
import com.xzq.appstore.core.downloader.DownloadExecutionControl
import com.xzq.appstore.core.downloader.DownloadRemoteMeta
import com.xzq.appstore.core.downloader.DownloadRequest
import com.xzq.appstore.core.downloader.DownloadStopReason
import com.xzq.appstore.core.downloader.FileDownloader
import com.xzq.appstore.core.installer.InstallEvent
import com.xzq.appstore.core.installer.InstallRequest
import com.xzq.appstore.core.installer.PackageInstaller
import com.xzq.appstore.core.installer.ApkIdentity
import com.xzq.appstore.core.installer.ApkIdentityValidator
import com.xzq.appstore.core.installer.ApkVerifier
import com.xzq.appstore.core.installer.ApkVerificationPolicy
import com.xzq.appstore.core.installer.ExpectedApkIdentity
import com.xzq.appstore.core.logger.AppLogger
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.AppInfo
import com.xzq.appstore.data.model.AppPlatform
import com.xzq.appstore.data.model.DownloadPreferences
import com.xzq.appstore.data.model.DownloadSegmentRecord
import com.xzq.appstore.data.model.DownloadTaskRecord
import com.xzq.appstore.data.model.InstalledApp
import com.xzq.appstore.data.model.PolicySettings
import com.xzq.appstore.data.model.UpgradeInfo
import com.xzq.appstore.data.repository.AppRepository
import com.xzq.appstore.domain.download.DefaultDownloadManager
import com.xzq.appstore.domain.install.DefaultInstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.policy.PolicyResult
import com.xzq.appstore.domain.state.DefaultStateCenter
import com.xzq.appstore.domain.state.DownloadStatus
import com.xzq.appstore.domain.state.InstallStatus
import com.xzq.appstore.domain.state.UpgradeStatus
import com.xzq.appstore.domain.text.BusinessText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DefaultUpgradeManagerTest {
    @Test
    fun `same display version with higher numeric code is an update`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "2.0.0", 1L)
        assertEquals(true, createManager().checkUpgrade(TEST_APP_ID))
    }

    @Test
    fun `newer looking display version cannot downgrade numeric code`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0-beta", 3L)
        assertEquals(false, createManager().checkUpgrade(TEST_APP_ID))
    }
    @Test
    fun `verified cached target upgrades offline without starting downloader`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        val apk = File(workDir, "cached.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        repository.saveApk(TEST_APP_ID, apk.absolutePath)
        val manager = createManager(policyCenter = OfflinePolicyCenter(), apkVerifier = targetVerifier(2L),
            fileDownloader = object : FileDownloader {
                override suspend fun download(request: DownloadRequest, control: DownloadExecutionControl, onEvent: suspend (DownloadEvent) -> Unit) {
                    fail("Verified cache must not start a download")
                }
            })
        manager.startUpgrade(TEST_APP_ID)
        assertEquals(UpgradeStatus.NONE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
        assertEquals(InstallStatus.INSTALLED, stateCenter.snapshot(TEST_APP_ID).installStatus)
    }

    @Test
    fun `older cached version cannot bypass offline download policy`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        repository.saveApk(TEST_APP_ID, File(workDir, "old.apk").apply { writeBytes(byteArrayOf(1)) }.absolutePath)
        createManager(policyCenter = OfflinePolicyCenter(), apkVerifier = targetVerifier(1L)).startUpgrade(TEST_APP_ID)
        assertEquals(UpgradeStatus.FAILED, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
        assertEquals(InstallStatus.INSTALLED, stateCenter.snapshot(TEST_APP_ID).installStatus)
        assertEquals("1.0.0", stateCenter.snapshot(TEST_APP_ID).installedVersion)
    }

    @Test
    fun `corrupt cached payload cannot bypass offline download policy`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        repository.checksumValue = "0".repeat(64)
        repository.saveApk(TEST_APP_ID, File(workDir, "corrupt.apk").apply { writeBytes(byteArrayOf(1)) }.absolutePath)
        createManager(policyCenter = OfflinePolicyCenter(), apkVerifier = targetVerifier(2L)).startUpgrade(TEST_APP_ID)
        assertEquals(UpgradeStatus.FAILED, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
        assertEquals(null, repository.stagedVersion)
    }

    private fun targetVerifier(versionCode: Long) = object : ApkVerifier {
        override fun verify(apkFile: File, expected: ExpectedApkIdentity, policy: ApkVerificationPolicy) =
            ApkIdentityValidator.validate(ApkIdentity(expected.packageName, versionCode, "2.0.0", setOf("test-signer")), expected, policy)
    }

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
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        val manager = createManager()
        val result = manager.checkUpgrade(TEST_APP_ID)
        assertEquals(true, result)
        assertEquals(UpgradeStatus.AVAILABLE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `checkUpgrade ignores updates for other platforms`() = runBlocking {
        repository.supportedPlatforms = setOf(AppPlatform.IOS)
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)

        val result = createManager().checkUpgrade(TEST_APP_ID)

        assertEquals(false, result)
        assertEquals(UpgradeStatus.NONE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `checkUpgrade 版本相同时返回 false`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "2.0.0", 2L)
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
    fun `startUpgrade blocks updates for other platforms`() = runBlocking {
        repository.supportedPlatforms = setOf(AppPlatform.WINDOWS)
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)

        createManager().startUpgrade(TEST_APP_ID)

        assertEquals(UpgradeStatus.FAILED, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
        assertEquals(BusinessText.STATUS_PLATFORM_UNSUPPORTED, stateCenter.snapshot(TEST_APP_ID).errorMessage)
    }

    @Test
    fun `startUpgrade 版本无升级时标记 NONE`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "2.0.0", 2L)
        val manager = createManager()
        manager.startUpgrade(TEST_APP_ID)
        assertEquals(UpgradeStatus.NONE, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `startUpgrade 成功完成下载和安装后升级状态回到 NONE`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
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
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)

        val manager = createManager()
        manager.startUpgrade(TEST_APP_ID)

        assertEquals("2.0.0", repository.stagedVersion)
    }

    @Test
    fun `checkAllUpgrades 返回所有有升级可用的应用`() = runBlocking {
        stateCenter.syncInstalled("app.a", "1.0.0", 1L)
        stateCenter.syncInstalled("app.b", "1.0.0", 1L)
        stateCenter.syncInstalled("app.c", "2.0.0", 2L)
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
        stateCenter.syncInstalled("app.a", "1.0.0", 1L)
        stateCenter.syncInstalled("app.b", "1.0.0", 1L)
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
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        val manager = createManager(fileDownloader = object : FileDownloader {
            override suspend fun download(request: DownloadRequest, control: DownloadExecutionControl, onEvent: suspend (DownloadEvent) -> Unit) {
                onEvent(DownloadEvent.Stopped(reason = DownloadStopReason.CANCELED, downloadedBytes = 0L, totalBytes = 1024L))
            }
        })

        withTimeout(1_000L) {
            manager.startUpgrade(TEST_APP_ID)
        }

        assertEquals(DownloadStatus.CANCELED, stateCenter.snapshot(TEST_APP_ID).downloadStatus)
        assertEquals(UpgradeStatus.FAILED, stateCenter.snapshot(TEST_APP_ID).upgradeStatus)
    }

    @Test
    fun `startUpgrade 下载长期未完成时标记下载超时`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        val manager = createManager(
            fileDownloader = object : FileDownloader {
                override suspend fun download(request: DownloadRequest, control: DownloadExecutionControl, onEvent: suspend (DownloadEvent) -> Unit) = Unit
            },
            waitTimeoutMs = 20L,
            pollIntervalMs = 1L,
        )

        manager.startUpgrade(TEST_APP_ID)

        assertEquals(BusinessText.UPGRADE_DOWNLOAD_TIMEOUT, stateCenter.snapshot(TEST_APP_ID).errorMessage)
    }

    @Test
    fun `startUpgrade 安装长期未完成时标记安装超时`() = runBlocking {
        stateCenter.syncInstalled(TEST_APP_ID, "1.0.0", 1L)
        val manager = createManager(
            packageInstaller = object : PackageInstaller {
                override suspend fun install(request: InstallRequest, onEvent: suspend (InstallEvent) -> Unit) {
                    onEvent(InstallEvent.Waiting)
                }
            },
            waitTimeoutMs = 20L,
            pollIntervalMs = 1L,
        )

        manager.startUpgrade(TEST_APP_ID)

        assertEquals(BusinessText.UPGRADE_INSTALL_TIMEOUT, stateCenter.snapshot(TEST_APP_ID).errorMessage)
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
        fileDownloader: FileDownloader? = null,
        packageInstaller: PackageInstaller? = null,
        pollIntervalMs: Long = 200L,
        waitTimeoutMs: Long = 30L * 60L * 1000L,
        apkVerifier: ApkVerifier? = null,
    ): DefaultUpgradeManager {
        File(workDir, "test.apk").apply { parentFile?.mkdirs() }
        val effectivePackageInstaller = packageInstaller ?: object : PackageInstaller {
            override suspend fun install(request: InstallRequest, onEvent: suspend (InstallEvent) -> Unit) {
                onEvent(InstallEvent.Waiting)
                onEvent(InstallEvent.SessionCreated(1))
                onEvent(InstallEvent.Installing)
                onEvent(InstallEvent.Success("2.0.0"))
            }
        }

        val effectiveFileDownloader = fileDownloader ?: object : FileDownloader {
            override suspend fun download(request: DownloadRequest, control: DownloadExecutionControl, onEvent: suspend (DownloadEvent) -> Unit) {
                request.targetFile.apply { parentFile?.mkdirs(); writeBytes(ByteArray(1024)) }
                onEvent(DownloadEvent.MetaReady(DownloadRemoteMeta(contentLength = 1024L, supportsRange = false)))
                onEvent(DownloadEvent.Running(1024L, 1024L, 128L))
                onEvent(DownloadEvent.Completed(request.targetFile, 1024L))
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
            pollIntervalMs = pollIntervalMs,
            waitTimeoutMs = waitTimeoutMs,
            apkVerifier = apkVerifier,
        )
    }

    private companion object {
        const val TEST_APP_ID = "test.app"
    }

    private open class AllowAllPolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(PolicySettings())
        override fun canDownload(appId: String) = PolicyResult(true)
        override fun canInstall(appId: String) = PolicyResult(true)
        override fun canUpgrade(appId: String) = PolicyResult(true)
        override fun observeSettings() = settingsFlow
        override fun getSettings() = settingsFlow.value
        override fun getStoredSettings() = settingsFlow.value
        override fun updateSettings(settings: PolicySettings) {
            settingsFlow.value = settings
        }
    }

    private class OfflinePolicyCenter : AllowAllPolicyCenter() {
        override fun canDownload(appId: String) = PolicyResult(false, "Wi-Fi unavailable")
        override fun canUpgrade(appId: String) = canDownload(appId)
    }

    private class DenyAllPolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(PolicySettings())
        override fun canDownload(appId: String) = PolicyResult(false, "禁止")
        override fun canInstall(appId: String) = PolicyResult(false, "禁止")
        override fun canUpgrade(appId: String) = PolicyResult(false, "禁止")
        override fun observeSettings() = settingsFlow
        override fun getSettings() = settingsFlow.value
        override fun getStoredSettings() = settingsFlow.value
        override fun updateSettings(settings: PolicySettings) {
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
        var supportedPlatforms: Set<AppPlatform> = setOf(AppPlatform.ANDROID)
        var checksumValue: String? = null

        fun saveApk(appId: String, path: String) {
            apkPaths[appId] = path
        }

        fun addInstalledApp(appId: String, name: String, versionName: String) {
            installedAppsList.add(InstalledApp(appId = appId, packageName = "com.nio.$appId", name = name, versionName = versionName))
        }

        override suspend fun getHomeApps() = emptyList<AppInfo>()
        override suspend fun getAppDetail(appId: String) = AppDetail(
            appId = appId, packageName = "com.nio.$appId", name = "App $appId",
            supportedPlatforms = supportedPlatforms,
            description = "", versionName = "2.0.0", versionCode = 2L, signerCertificateSha256 = listOf("test-signer"), apkUrl = "https://example.com/$appId.apk",
            checksumType = "SHA-256", checksumValue = checksumValue,
        )

        override suspend fun getInstalledApps() = if (installedAppsList.isNotEmpty()) {
            installedAppsList.toList()
        } else {
            listOf(InstalledApp(appId = TEST_APP_ID, packageName = "com.nio.test", name = "Test", versionName = "1.0.0"))
        }

        override suspend fun markInstalled(appId: String) = Unit
        override suspend fun isInstalled(appId: String) = true
        override suspend fun saveDownloadedApk(appId: String, apkPath: String) {
            apkPaths[appId] = apkPath
        }

        override suspend fun getDownloadedApk(appId: String) = apkPaths[appId]
        override suspend fun clearDownloadedApk(appId: String) {
            apkPaths.remove(appId)
        }

        override suspend fun getUpgradeInfo(appId: String) = UpgradeInfo(appId = appId, latestVersion = "2.0.0", apkUrl = "", hasUpgrade = true, latestVersionCode = 2L)
        override suspend fun stageUpgrade(appId: String, versionName: String) {
            stagedVersion = versionName
        }

        override suspend fun peekStagedUpgradeVersion(appId: String) = stagedVersion
        override suspend fun saveDownloadTask(record: DownloadTaskRecord) = Unit
        override suspend fun getDownloadTask(appId: String): DownloadTaskRecord? = null
        override suspend fun getAllDownloadTasks() = emptyList<DownloadTaskRecord>()
        override suspend fun removeDownloadTask(appId: String) = Unit
        override suspend fun clearCompletedDownloadTasks() = 0
        override suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) = Unit
        override suspend fun getDownloadSegments(appId: String) = emptyList<DownloadSegmentRecord>()
        override suspend fun getOrCreateDownloadFile(appId: String): File {
            return File(workDir, "downloads/$appId.apk").apply { parentFile?.mkdirs() }
        }

        override suspend fun getDownloadPreferences() = DownloadPreferences()
        override suspend fun saveDownloadPreferences(preferences: DownloadPreferences) = Unit
        override fun getPolicySettings() = PolicySettings()
        override fun openApp(packageName: String) = false
    }
}
