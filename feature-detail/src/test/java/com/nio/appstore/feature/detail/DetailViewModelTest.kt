package com.nio.appstore.feature.detail

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.policy.PolicyResult
import com.nio.appstore.domain.state.DefaultStateCenter
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load 会加载详情并触发升级检查`() = runTest {
        val appManager = FakeAppManager()
        val upgradeManager = RecordingUpgradeManager()
        val stateCenter = DefaultStateCenter()
        val viewModel = DetailViewModel(
            appManager = appManager,
            downloadManager = RecordingDownloadManager(),
            installManager = RecordingInstallManager(),
            upgradeManager = upgradeManager,
            stateCenter = stateCenter,
            policyCenter = FakePolicyCenter(),
        )

        viewModel.load(TEST_APP_DETAIL.appId)
        advanceUntilIdle()

        assertEquals(TEST_APP_DETAIL, viewModel.uiState.value.appDetail)
        assertEquals(TEST_APP_DETAIL.appId, upgradeManager.checkedUpgradeAppId)
        assertEquals(PrimaryAction.DOWNLOAD, viewModel.uiState.value.primaryAction)
    }

    @Test
    fun `onPrimaryClick 在打开动作时会使用详情包名打开应用`() = runTest {
        val appManager = FakeAppManager()
        val stateCenter = DefaultStateCenter()
        val viewModel = DetailViewModel(
            appManager = appManager,
            downloadManager = RecordingDownloadManager(),
            installManager = RecordingInstallManager(),
            upgradeManager = RecordingUpgradeManager(),
            stateCenter = stateCenter,
            policyCenter = FakePolicyCenter(),
        )

        viewModel.load(TEST_APP_DETAIL.appId)
        advanceUntilIdle()
        stateCenter.syncInstalled(TEST_APP_DETAIL.appId, TEST_APP_DETAIL.versionName)
        advanceUntilIdle()

        viewModel.onPrimaryClick()
        advanceUntilIdle()

        assertEquals(TEST_APP_DETAIL.packageName, appManager.openedPackageName)
    }

    @Test
    fun `onPrimaryClick 在暂停状态时会恢复当前应用下载`() = runTest {
        val downloadManager = RecordingDownloadManager()
        val stateCenter = DefaultStateCenter()
        val viewModel = DetailViewModel(
            appManager = FakeAppManager(),
            downloadManager = downloadManager,
            installManager = RecordingInstallManager(),
            upgradeManager = RecordingUpgradeManager(),
            stateCenter = stateCenter,
            policyCenter = FakePolicyCenter(),
        )

        viewModel.load(TEST_APP_DETAIL.appId)
        advanceUntilIdle()
        stateCenter.updateDownload(
            appId = TEST_APP_DETAIL.appId,
            status = DownloadStatus.PAUSED,
            progress = TEST_PROGRESS,
        )
        advanceUntilIdle()

        viewModel.onPrimaryClick()
        advanceUntilIdle()

        assertEquals(TEST_APP_DETAIL.appId, downloadManager.resumedAppId)
        assertNull(downloadManager.startedAppId)
    }

    private class FakeAppManager : AppManager {
        /** 最近一次被请求打开的包名。 */
        var openedPackageName: String? = null

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
            return true
        }
    }

    private class RecordingDownloadManager : DownloadManager {
        /** 最近一次启动下载的应用。 */
        var startedAppId: String? = null

        /** 最近一次恢复下载的应用。 */
        var resumedAppId: String? = null

        override suspend fun startDownload(appId: String) {
            startedAppId = appId
        }

        override suspend fun pauseDownload(appId: String) = Unit

        override suspend fun resumeDownload(appId: String) {
            resumedAppId = appId
        }

        override suspend fun cancelDownload(appId: String) = Unit

        override suspend fun removeTask(appId: String, clearFile: Boolean) = Unit

        override suspend fun clearCompletedTasks(): Int = 0

        override suspend fun retryFailedTasks(): Int = 0

        override suspend fun getPreferences(): DownloadPreferences = DownloadPreferences()

        override suspend fun updatePreferences(preferences: DownloadPreferences) = Unit
    }

    private class RecordingInstallManager : InstallManager {
        /** 最近一次发起安装的应用。 */
        var installedAppId: String? = null

        override suspend fun install(appId: String) {
            installedAppId = appId
        }

        override suspend fun clearFailed(appId: String) = Unit
    }

    private class RecordingUpgradeManager : UpgradeManager {
        /** 最近一次检查升级的应用。 */
        var checkedUpgradeAppId: String? = null

        override suspend fun startUpgrade(appId: String) = Unit

        override suspend fun checkUpgrade(appId: String): Boolean {
            checkedUpgradeAppId = appId
            return true
        }

        override suspend fun checkAllUpgrades(): List<String> = emptyList()

        override suspend fun startBatchUpgrade(appIds: List<String>) = Unit
    }

    private class FakePolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(com.nio.appstore.data.model.PolicySettings())

        override fun canDownload(appId: String): PolicyResult = PolicyResult(true)

        override fun canInstall(appId: String): PolicyResult = PolicyResult(true)

        override fun canUpgrade(appId: String): PolicyResult = PolicyResult(true)

        override fun observeSettings() = settingsFlow

        override fun getSettings() = settingsFlow.value

        override fun getStoredSettings() = settingsFlow.value

        override fun updateSettings(settings: com.nio.appstore.data.model.PolicySettings) {
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
        /** 测试进度百分比。 */
        const val TEST_PROGRESS = 64

        /** 测试详情模型。 */
        val TEST_APP_DETAIL = AppDetail(
            appId = "demo.detail",
            packageName = "com.nio.demo.detail",
            name = "Demo Detail",
            description = "detail test app",
            versionName = "1.0.0",
            apkUrl = "https://example.com/detail.apk",
        )
    }
}
