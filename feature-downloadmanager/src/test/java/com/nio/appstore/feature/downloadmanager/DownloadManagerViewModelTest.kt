package com.nio.appstore.feature.downloadmanager

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.TaskOverallStatus
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.policy.PolicyResult
import com.nio.appstore.domain.state.DefaultStateCenter
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onPrimaryClick 为恢复动作时会恢复下载`() = runTest {
        val appManager = FakeAppManager()
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val viewModel = DownloadManagerViewModel(
            appManager = appManager,
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = RecordingUpgradeManager(),
            policyCenter = FakePolicyCenter(),
        )

        viewModel.onPrimaryClick(TEST_RESUME_TASK)
        advanceUntilIdle()

        assertEquals(TEST_RESUME_TASK.appId, downloadManager.resumedAppId)
        assertNull(installManager.installedAppId)
    }

    @Test
    fun `onInstallPrimaryClick 为打开动作时会打开应用`() = runTest {
        val appManager = FakeAppManager()
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val viewModel = DownloadManagerViewModel(
            appManager = appManager,
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = RecordingUpgradeManager(),
            policyCenter = FakePolicyCenter(),
        )

        viewModel.onInstallPrimaryClick(TEST_OPEN_INSTALL_TASK)
        advanceUntilIdle()

        assertEquals(TEST_OPEN_INSTALL_TASK.packageName, appManager.openedPackageName)
        assertNull(downloadManager.resumedAppId)
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

        override suspend fun getDownloadTasks(): List<DownloadTaskViewData> = listOf(TEST_RESUME_TASK)

        override suspend fun getUpgradeManageApps(): List<AppViewData> = emptyList()

        override suspend fun getInstallTasks(): List<InstallTaskViewData> = listOf(TEST_OPEN_INSTALL_TASK)

        override suspend fun getUpgradeTasks(): List<UpgradeTaskViewData> = emptyList()

        override suspend fun getDownloadTaskStats(): TaskCenterStats = TaskCenterStats(activeCount = 1)

        override suspend fun getInstallTaskStats(): TaskCenterStats = TaskCenterStats(completedCount = 1)

        override suspend fun getUpgradeTaskStats(): TaskCenterStats = TaskCenterStats()

        override fun getPolicyPrompt(): String = ""

        override fun openApp(packageName: String): Boolean {
            openedPackageName = packageName
            return true
        }
    }

    private class RecordingDownloadManager : DownloadManager {
        /** 最近一次恢复下载的应用。 */
        var resumedAppId: String? = null

        override suspend fun startDownload(appId: String) = Unit

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
        override suspend fun startUpgrade(appId: String) = Unit

        override suspend fun checkUpgrade(appId: String): Boolean = true
    }

    private class FakePolicyCenter : PolicyCenter {
        override fun canDownload(appId: String): PolicyResult = PolicyResult(allow = true)

        override fun canInstall(appId: String): PolicyResult = PolicyResult(allow = true)

        override fun canUpgrade(appId: String): PolicyResult = PolicyResult(allow = true)

        override fun getSettings(): PolicySettings = PolicySettings()

        override fun updateSettings(settings: PolicySettings) = Unit
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
        val TEST_APP_DETAIL = AppDetail(
            appId = "demo.download.center",
            packageName = "com.nio.demo.download.center",
            name = "Download Center",
            description = "download center test app",
            versionName = "1.0.0",
            apkUrl = "https://example.com/download-center.apk",
        )

        /** 恢复下载任务。 */
        val TEST_RESUME_TASK = DownloadTaskViewData(
            appId = "demo.resume.task",
            name = "Resume Task",
            versionName = "1.0.0",
            stateText = "已暂停",
            statusTone = StatusTone.WARNING,
            overallStatus = TaskOverallStatus.ACTIVE,
            progress = 42,
            primaryAction = PrimaryAction.RESUME,
            sizeText = "42MB/100MB",
            speedText = "0KB/s",
            timeText = "刚刚",
            pathText = "/data/demo.apk",
            secondaryActionText = "移除",
            showSecondaryAction = true,
            installed = false,
        )

        /** 打开安装任务。 */
        val TEST_OPEN_INSTALL_TASK = InstallTaskViewData(
            appId = "demo.install.open",
            packageName = "com.nio.demo.install.open",
            name = "Installed App",
            versionName = "2.0.0",
            stateText = "已安装",
            statusTone = StatusTone.SUCCESS,
            overallStatus = TaskOverallStatus.COMPLETED,
            primaryAction = PrimaryAction.OPEN,
        )
    }
}
