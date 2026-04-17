package com.nio.appstore.domain.install

import com.nio.appstore.core.installer.InstallEvent
import com.nio.appstore.core.installer.InstallFailureCode
import com.nio.appstore.core.installer.InstallRequest
import com.nio.appstore.core.installer.PackageInstaller
import com.nio.appstore.core.logger.AppLogger
import com.nio.appstore.core.tracker.EventTracker
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.policy.PolicyResult
import com.nio.appstore.domain.state.DefaultStateCenter
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.InstallStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DefaultInstallManagerTest {

    private lateinit var workDir: File
    private lateinit var stateCenter: DefaultStateCenter
    private lateinit var repository: FakeInstallRepository
    private lateinit var installer: ControllablePackageInstaller

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("install-manager-test").toFile()
        stateCenter = DefaultStateCenter()
        repository = FakeInstallRepository(workDir)
        installer = ControllablePackageInstaller()
    }

    private fun createManager(
        policyCenter: PolicyCenter = AllowAllPolicyCenter(),
        packageInstaller: PackageInstaller = installer,
    ): DefaultInstallManager {
        return DefaultInstallManager(
            repository = repository,
            stateCenter = stateCenter,
            policyCenter = policyCenter,
            packageInstaller = packageInstaller,
            logger = QuietLogger(),
            tracker = QuietTracker(),
        )
    }

    @Test
    fun `install 策略拦截时直接标记安装失败`() = runBlocking {
        val manager = createManager(policyCenter = DenyAllPolicyCenter())

        manager.install(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(InstallStatus.FAILED, state.installStatus)
        assertTrue(state.errorMessage!!.contains("安装受限"))
    }

    @Test
    fun `install APK 路径缺失时标记下载和安装都失败`() = runBlocking {
        val manager = createManager()
        // 不保存 APK 路径

        manager.install(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(DownloadStatus.FAILED, state.downloadStatus)
        assertEquals(InstallStatus.FAILED, state.installStatus)
        assertEquals(InstallFailureCode.APK_MISSING.name, state.errorCode)
    }

    @Test
    fun `install 成功时更新安装状态和下载状态`() = runBlocking {
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)
        installer.scenario = InstallScenario.SUCCESS

        val manager = createManager()
        manager.install(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(InstallStatus.INSTALLED, state.installStatus)
        assertEquals(DownloadStatus.COMPLETED, state.downloadStatus)
        assertEquals("1.0.0", state.installedVersion)
        assertTrue(repository.installedApps.contains(TEST_APP_ID))
        assertTrue(repository.taskRemoved.contains(TEST_APP_ID))
    }

    @Test
    fun `install APK损坏时同时清理下载产物并标记失败`() = runBlocking {
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)
        installer.scenario = InstallScenario.APK_INVALID

        val manager = createManager()
        manager.install(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(InstallStatus.FAILED, state.installStatus)
        assertEquals(DownloadStatus.FAILED, state.downloadStatus)
        assertNull(repository.getApk(TEST_APP_ID))
    }

    @Test
    fun `install 底层安装失败时正确标记安装失败状态`() = runBlocking {
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)
        installer.scenario = InstallScenario.GENERIC_FAILURE

        val manager = createManager()
        manager.install(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(InstallStatus.FAILED, state.installStatus)
        assertEquals(InstallFailureCode.SESSION_COMMIT_FAILED.name, state.errorCode)
    }

    @Test
    fun `install 正确传递升级目标版本`() = runBlocking {
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)
        repository.stageUpgradeVersion(TEST_APP_ID, "2.0.0")
        installer.scenario = InstallScenario.SUCCESS

        val manager = createManager()
        manager.install(TEST_APP_ID)

        assertEquals("2.0.0", installer.capturedRequest?.targetVersion)
    }

    @Test
    fun `clearFailed 本地APK有效时恢复到等待安装状态`() = runBlocking {
        val apkFile = File(workDir, "test.apk").apply { writeBytes(ByteArray(1024)) }
        repository.saveApk(TEST_APP_ID, apkFile.absolutePath)
        stateCenter.updateInstall(TEST_APP_ID, InstallStatus.FAILED, errorMessage = "安装失败", errorCode = "UNKNOWN")

        val manager = createManager()
        manager.clearFailed(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(InstallStatus.WAITING, state.installStatus)
        assertEquals(DownloadStatus.COMPLETED, state.downloadStatus)
        assertNull(state.errorMessage)
        assertNull(state.errorCode)
    }

    @Test
    fun `clearFailed 本地APK无效时复位下载和安装状态`() = runBlocking {
        stateCenter.updateInstall(TEST_APP_ID, InstallStatus.FAILED, errorMessage = "安装失败", errorCode = "UNKNOWN")

        val manager = createManager()
        manager.clearFailed(TEST_APP_ID)

        val state = stateCenter.snapshot(TEST_APP_ID)
        assertEquals(InstallStatus.NOT_INSTALLED, state.installStatus)
        assertEquals(DownloadStatus.IDLE, state.downloadStatus)
        assertNull(state.errorMessage)
    }

    private companion object {
        const val TEST_APP_ID = "test.app"
    }

    private class AllowAllPolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(com.nio.appstore.data.model.PolicySettings())
        override fun canDownload(appId: String) = PolicyResult(true)
        override fun canInstall(appId: String) = PolicyResult(true)
        override fun canUpgrade(appId: String) = PolicyResult(true)
        override fun observeSettings() = settingsFlow
        override fun getSettings() = settingsFlow.value
        override fun getStoredSettings() = settingsFlow.value
        override fun updateSettings(settings: com.nio.appstore.data.model.PolicySettings) {
            settingsFlow.value = settings
        }
    }

    private class DenyAllPolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(com.nio.appstore.data.model.PolicySettings())
        override fun canDownload(appId: String) = PolicyResult(false, "禁止下载")
        override fun canInstall(appId: String) = PolicyResult(false, "禁止安装")
        override fun canUpgrade(appId: String) = PolicyResult(false, "禁止升级")
        override fun observeSettings() = settingsFlow
        override fun getSettings() = settingsFlow.value
        override fun getStoredSettings() = settingsFlow.value
        override fun updateSettings(settings: com.nio.appstore.data.model.PolicySettings) {
            settingsFlow.value = settings
        }
    }

    private class QuietLogger : AppLogger() {
        override fun d(tag: String, message: String) = Unit
    }

    private class QuietTracker : EventTracker() {
        override fun track(event: String) = Unit
    }

    private enum class InstallScenario { SUCCESS, APK_INVALID, GENERIC_FAILURE, SESSION_ERROR }

    private class ControllablePackageInstaller : PackageInstaller {
        var scenario: InstallScenario = InstallScenario.SUCCESS
        var capturedRequest: InstallRequest? = null

        override suspend fun install(
            request: InstallRequest,
            onEvent: suspend (InstallEvent) -> Unit,
        ) {
            capturedRequest = request
            when (scenario) {
                InstallScenario.SUCCESS -> {
                    onEvent(InstallEvent.Waiting)
                    onEvent(InstallEvent.SessionCreated(1))
                    onEvent(InstallEvent.PendingUserAction(1, "确认安装"))
                    onEvent(InstallEvent.Installing)
                    onEvent(InstallEvent.Progress(1, 50))
                    onEvent(InstallEvent.Success("1.0.0"))
                }
                InstallScenario.APK_INVALID -> {
                    onEvent(InstallEvent.Waiting)
                    onEvent(InstallEvent.Failed(InstallFailureCode.APK_INVALID, "APK 无效"))
                }
                InstallScenario.GENERIC_FAILURE -> {
                    onEvent(InstallEvent.Waiting)
                    onEvent(InstallEvent.SessionCreated(1))
                    onEvent(InstallEvent.Failed(InstallFailureCode.SESSION_COMMIT_FAILED, "提交失败"))
                }
                InstallScenario.SESSION_ERROR -> {
                    onEvent(InstallEvent.Waiting)
                    onEvent(InstallEvent.Failed(InstallFailureCode.SESSION_CREATE_FAILED, "会话创建失败"))
                }
            }
        }
    }

    private class FakeInstallRepository(private val workDir: File) : AppRepository {
        private val apkPaths = mutableMapOf<String, String>()
        private val stagedVersions = mutableMapOf<String, String>()
        val installedApps = mutableSetOf<String>()
        val taskRemoved = mutableSetOf<String>()

        fun saveApk(appId: String, path: String) { apkPaths[appId] = path }
        fun getApk(appId: String): String? = apkPaths[appId]
        fun stageUpgradeVersion(appId: String, version: String) { stagedVersions[appId] = version }

        override suspend fun getHomeApps() = emptyList<com.nio.appstore.data.model.AppInfo>()
        override suspend fun getAppDetail(appId: String) = AppDetail(
            appId = appId,
            packageName = "com.nio.test",
            name = "Test App",
            description = "test",
            versionName = "1.0.0",
            apkUrl = "https://example.com/test.apk",
        )
        override suspend fun getInstalledApps() = emptyList<com.nio.appstore.data.model.InstalledApp>()
        override suspend fun markInstalled(appId: String) { installedApps.add(appId) }
        override suspend fun isInstalled(appId: String) = installedApps.contains(appId)
        override suspend fun saveDownloadedApk(appId: String, apkPath: String) { apkPaths[appId] = apkPath }
        override suspend fun getDownloadedApk(appId: String) = apkPaths[appId]
        override suspend fun clearDownloadedApk(appId: String) { apkPaths.remove(appId) }
        override suspend fun getUpgradeInfo(appId: String) = com.nio.appstore.data.model.UpgradeInfo(
            appId = appId, latestVersion = "2.0.0", apkUrl = "", hasUpgrade = true,
        )
        override suspend fun stageUpgrade(appId: String, versionName: String) { stagedVersions[appId] = versionName }
        override suspend fun peekStagedUpgradeVersion(appId: String) = stagedVersions[appId]
        override suspend fun saveDownloadTask(record: com.nio.appstore.data.model.DownloadTaskRecord) = Unit
        override suspend fun getDownloadTask(appId: String): com.nio.appstore.data.model.DownloadTaskRecord? = null
        override suspend fun getAllDownloadTasks() = emptyList<com.nio.appstore.data.model.DownloadTaskRecord>()
        override suspend fun removeDownloadTask(appId: String) { taskRemoved.add(appId) }
        override suspend fun clearCompletedDownloadTasks() = 0
        override suspend fun saveDownloadSegments(appId: String, segments: List<com.nio.appstore.data.model.DownloadSegmentRecord>) = Unit
        override suspend fun getDownloadSegments(appId: String) = emptyList<com.nio.appstore.data.model.DownloadSegmentRecord>()
        override suspend fun getOrCreateDownloadFile(appId: String): File {
            return File(workDir, "downloads/$appId.apk").apply { parentFile?.mkdirs() }
        }
        override suspend fun getDownloadPreferences() = com.nio.appstore.data.model.DownloadPreferences()
        override suspend fun saveDownloadPreferences(preferences: com.nio.appstore.data.model.DownloadPreferences) = Unit
        override fun getPolicySettings() = com.nio.appstore.data.model.PolicySettings()
        override fun openApp(packageName: String) = false
    }
}
