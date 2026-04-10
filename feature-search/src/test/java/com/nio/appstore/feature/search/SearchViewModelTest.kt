package com.nio.appstore.feature.search

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
import com.nio.appstore.domain.state.DefaultStateCenter
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.upgrade.UpgradeManager

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onPrimaryClick 为恢复动作时会恢复下载`() = runTest {
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = SearchViewModel(
            appManager = FakeAppManager(),
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_RESUME_APP)
        advanceUntilIdle()

        assertEquals(TEST_RESUME_APP.appId, downloadManager.resumedAppId)
        assertNull(installManager.installedAppId)
        assertNull(upgradeManager.startedUpgradeAppId)
    }

    @Test
    fun `onPrimaryClick 为升级动作时会启动升级`() = runTest {
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = SearchViewModel(
            appManager = FakeAppManager(),
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_UPGRADE_APP)
        advanceUntilIdle()

        assertEquals(TEST_UPGRADE_APP.appId, upgradeManager.startedUpgradeAppId)
        assertNull(downloadManager.startedAppId)
        assertNull(installManager.installedAppId)
    }

    @Test
    fun `onPrimaryClick 为禁用动作时不会触发任何业务入口`() = runTest {
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = SearchViewModel(
            appManager = FakeAppManager(),
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_DISABLED_APP)
        advanceUntilIdle()

        assertNull(downloadManager.startedAppId)
        assertNull(downloadManager.resumedAppId)
        assertNull(installManager.installedAppId)
        assertNull(upgradeManager.startedUpgradeAppId)
        assertNull(upgradeManager.checkedUpgradeAppId)
    }

    private class FakeAppManager : AppManager {
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

        override fun openApp(packageName: String): Boolean = true
    }

    private class RecordingDownloadManager : DownloadManager {
        /** 最近一次启动下载的 appId。 */
        var startedAppId: String? = null

        /** 最近一次恢复下载的 appId。 */
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
        /** 最近一次发起安装的 appId。 */
        var installedAppId: String? = null

        override suspend fun install(appId: String) {
            installedAppId = appId
        }

        override suspend fun clearFailed(appId: String) = Unit
    }

    private class RecordingUpgradeManager : UpgradeManager {
        /** 最近一次检查升级的 appId。 */
        var checkedUpgradeAppId: String? = null

        /** 最近一次发起升级的 appId。 */
        var startedUpgradeAppId: String? = null

        override suspend fun startUpgrade(appId: String) {
            startedUpgradeAppId = appId
        }

        override suspend fun checkUpgrade(appId: String): Boolean {
            checkedUpgradeAppId = appId
            return true
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
        val TEST_APP_DETAIL = AppDetail(
            appId = "demo.search",
            packageName = "com.nio.demo.search",
            name = "Demo Search",
            description = "demo search app",
            versionName = "1.0.0",
            apkUrl = "https://example.com/demo-search.apk",
        )

        /** 恢复动作卡片。 */
        val TEST_RESUME_APP = AppViewData(
            appId = "demo.resume",
            name = "Demo Resume",
            description = "resume test app",
            versionName = "1.0.3",
            packageName = "com.nio.demo.resume",
            stateText = "已暂停",
            statusTone = StatusTone.WARNING,
            primaryAction = PrimaryAction.RESUME,
            progress = 40,
        )

        /** 升级动作卡片。 */
        val TEST_UPGRADE_APP = AppViewData(
            appId = "demo.upgrade",
            name = "Demo Upgrade",
            description = "upgrade test app",
            versionName = "2.0.0",
            packageName = "com.nio.demo.upgrade",
            stateText = "可升级",
            statusTone = StatusTone.WARNING,
            primaryAction = PrimaryAction.UPGRADE,
            installed = true,
        )

        /** 禁用动作卡片。 */
        val TEST_DISABLED_APP = AppViewData(
            appId = "demo.disabled",
            name = "Demo Disabled",
            description = "disabled test app",
            versionName = "2.0.1",
            packageName = "com.nio.demo.disabled",
            stateText = "处理中",
            statusTone = StatusTone.INFO,
            primaryAction = PrimaryAction.DISABLED,
        )
    }
}
