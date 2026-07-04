package com.xzq.appstore.feature.home

import com.xzq.appstore.common.ui.StatusTone
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.data.model.DownloadTaskViewData
import com.xzq.appstore.data.model.InstallTaskViewData
import com.xzq.appstore.data.model.TaskCenterStats
import com.xzq.appstore.data.model.UpgradeTaskViewData
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.policy.PolicyResult
import com.xzq.appstore.domain.state.DefaultStateCenter
import com.xzq.appstore.domain.state.PrimaryAction
import com.xzq.appstore.domain.upgrade.UpgradeBatchResult
import com.xzq.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onPrimaryClick 为下载动作时会启动下载`() =
        runTest {
            val appManager = FakeAppManager()
            val downloadManager = RecordingDownloadManager()
            val installManager = RecordingInstallManager()
            val upgradeManager = RecordingUpgradeManager()
            val viewModel =
                HomeViewModel(
                    appManager = appManager,
                    stateCenter = DefaultStateCenter(),
                    downloadManager = downloadManager,
                    installManager = installManager,
                    upgradeManager = upgradeManager,
                    policyCenter = FakePolicyCenter(),
                )

            viewModel.onPrimaryClick(TEST_DOWNLOAD_APP)
            advanceUntilIdle()

            assertEquals(TEST_DOWNLOAD_APP.appId, downloadManager.startedAppIds.single())
            assertTrue(installManager.installedAppIds.isEmpty())
            assertTrue(upgradeManager.startedUpgradeAppIds.isEmpty())
        }

    @Test
    fun `onPrimaryClick 为安装动作时会触发安装并刷新升级检查`() =
        runTest {
            val appManager = FakeAppManager()
            val downloadManager = RecordingDownloadManager()
            val installManager = RecordingInstallManager()
            val upgradeManager = RecordingUpgradeManager()
            val viewModel =
                HomeViewModel(
                    appManager = appManager,
                    stateCenter = DefaultStateCenter(),
                    downloadManager = downloadManager,
                    installManager = installManager,
                    upgradeManager = upgradeManager,
                    policyCenter = FakePolicyCenter(),
                )

            viewModel.onPrimaryClick(TEST_INSTALL_APP)
            advanceUntilIdle()

            assertEquals(TEST_INSTALL_APP.appId, installManager.installedAppIds.single())
            assertEquals(TEST_INSTALL_APP.appId, upgradeManager.checkedUpgradeAppIds.single())
            assertTrue(downloadManager.startedAppIds.isEmpty())
        }

    @Test
    fun `onPrimaryClick 为打开动作时会直接打开目标应用`() =
        runTest {
            val appManager = FakeAppManager()
            val downloadManager = RecordingDownloadManager()
            val installManager = RecordingInstallManager()
            val upgradeManager = RecordingUpgradeManager()
            val viewModel =
                HomeViewModel(
                    appManager = appManager,
                    stateCenter = DefaultStateCenter(),
                    downloadManager = downloadManager,
                    installManager = installManager,
                    upgradeManager = upgradeManager,
                    policyCenter = FakePolicyCenter(),
                )

            viewModel.onPrimaryClick(TEST_OPEN_APP)
            advanceUntilIdle()

            assertEquals(TEST_OPEN_APP.packageName, appManager.openedPackageName)
            assertTrue(appManager.openResult)
            assertTrue(downloadManager.startedAppIds.isEmpty())
        }

    private class FakeAppManager : AppManager {
        /** 最近一次被请求打开的包名。 */
        var openedPackageName: String? = null

        /** 测试中统一返回成功打开。 */
        val openResult: Boolean = true

        override suspend fun getHomeApps(): List<AppViewData> = emptyList()

        override suspend fun getAppDetail(appId: String): AppDetail = TEST_APP_DETAIL

        override suspend fun getMyApps(): List<AppViewData> = emptyList()

        override suspend fun getHomeAppViewData(appId: String): AppViewData? = null

        override suspend fun searchApps(keyword: String): List<AppViewData> = emptyList()

        override suspend fun getDownloadManageApps(): List<AppViewData> = emptyList()

        override suspend fun getDownloadTasks(): List<DownloadTaskViewData> = emptyList()

        override suspend fun getUpgradeManageApps(): List<AppViewData> = emptyList()

        override suspend fun getInstallTasks(): List<InstallTaskViewData> = emptyList()

        override suspend fun getUpgradeTasks(): List<UpgradeTaskViewData> = emptyList()

        override suspend fun getDownloadTaskStats(): TaskCenterStats = TaskCenterStats()

        override suspend fun getInstallTaskStats(): TaskCenterStats = TaskCenterStats()

        override suspend fun getUpgradeTaskStats(): TaskCenterStats = TaskCenterStats()

        override fun getPolicyPrompt(): String = ""

        override fun openApp(packageName: String): Boolean {
            openedPackageName = packageName
            return openResult
        }
    }

    private class RecordingDownloadManager : DownloadManager {
        /** 累计记录所有启动下载请求，便于断言精确调用次数。 */
        val startedAppIds = mutableListOf<String>()

        /** 启动下载时按需抛出，便于覆盖错误路径。 */
        var errorForStart: Throwable? = null

        override suspend fun startDownload(appId: String) {
            errorForStart?.let { throw it }
            startedAppIds.add(appId)
        }

        override suspend fun pauseDownload(appId: String) = Unit

        override suspend fun resumeDownload(appId: String) = Unit

        override suspend fun cancelDownload(appId: String) = Unit

        override suspend fun removeTask(
            appId: String,
            clearFile: Boolean,
        ) = Unit

        override suspend fun clearCompletedTasks(): Int = 0

        override suspend fun retryFailedTasks(): Int = 0

        override suspend fun getPreferences() = throw UnsupportedOperationException("not used in test")

        override suspend fun updatePreferences(preferences: com.xzq.appstore.data.model.DownloadPreferences) = Unit
    }

    private class RecordingInstallManager : InstallManager {
        /** 累计记录所有安装请求，便于断言精确调用次数。 */
        val installedAppIds = mutableListOf<String>()

        override suspend fun install(appId: String) {
            installedAppIds.add(appId)
        }

        override suspend fun clearFailed(appId: String) = Unit
    }

    private class RecordingUpgradeManager : UpgradeManager {
        /** 累计记录所有检查升级请求，便于断言精确调用次数。 */
        val checkedUpgradeAppIds = mutableListOf<String>()

        /** 累计记录所有启动升级请求，便于断言精确调用次数。 */
        val startedUpgradeAppIds = mutableListOf<String>()

        override suspend fun startUpgrade(appId: String) {
            startedUpgradeAppIds.add(appId)
        }

        override suspend fun checkUpgrade(appId: String): Boolean {
            checkedUpgradeAppIds.add(appId)
            return true
        }

        override suspend fun checkAllUpgrades(): List<String> = emptyList()

        override suspend fun startBatchUpgrade(appIds: List<String>) = UpgradeBatchResult()
    }

    private class FakePolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow =
            MutableStateFlow(
                com.xzq.appstore.data.model
                    .PolicySettings(),
            )

        override fun canDownload(appId: String): PolicyResult = PolicyResult(true)

        override fun canInstall(appId: String): PolicyResult = PolicyResult(true)

        override fun canUpgrade(appId: String): PolicyResult = PolicyResult(true)

        override fun observeSettings() = settingsFlow

        override fun getSettings() = settingsFlow.value

        override fun getStoredSettings() = settingsFlow.value

        override fun updateSettings(settings: com.xzq.appstore.data.model.PolicySettings) {
            settingsFlow.value = settings
        }
    }

    class MainDispatcherRule(
        /** 测试主线程调度器。 */
        private val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        /** 测试详情模型。 */
        val TEST_APP_DETAIL =
            AppDetail(
                appId = "demo.home",
                packageName = "com.nio.demo.home",
                name = "Demo Home",
                description = "demo home app",
                versionName = "1.0.0",
                apkUrl = "https://example.com/demo-home.apk",
            )

        /** 下载动作卡片。 */
        val TEST_DOWNLOAD_APP =
            AppViewData(
                appId = "demo.download",
                name = "Demo Download",
                description = "download test app",
                versionName = "1.0.0",
                packageName = "com.nio.demo.download",
                stateText = "待下载",
                statusTone = StatusTone.NEUTRAL,
                primaryAction = PrimaryAction.DOWNLOAD,
            )

        /** 安装动作卡片。 */
        val TEST_INSTALL_APP =
            AppViewData(
                appId = "demo.install",
                name = "Demo Install",
                description = "install test app",
                versionName = "1.0.1",
                packageName = "com.nio.demo.install",
                stateText = "下载完成",
                statusTone = StatusTone.WARNING,
                primaryAction = PrimaryAction.INSTALL,
                progress = 100,
            )

        /** 打开动作卡片。 */
        val TEST_OPEN_APP =
            AppViewData(
                appId = "demo.open",
                name = "Demo Open",
                description = "open test app",
                versionName = "1.0.2",
                packageName = "com.nio.demo.open",
                stateText = "已安装",
                statusTone = StatusTone.SUCCESS,
                primaryAction = PrimaryAction.OPEN,
                installed = true,
            )
    }
}
