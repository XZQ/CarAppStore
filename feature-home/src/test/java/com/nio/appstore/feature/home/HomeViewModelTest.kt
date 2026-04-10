package com.nio.appstore.feature.home

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
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
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onPrimaryClick 为下载动作时会启动下载`() = runTest {
        val appManager = FakeAppManager()
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = HomeViewModel(
            appManager = appManager,
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_DOWNLOAD_APP)
        advanceUntilIdle()

        assertEquals(TEST_DOWNLOAD_APP.appId, downloadManager.startedAppId)
        assertNull(installManager.installedAppId)
        assertNull(upgradeManager.startedUpgradeAppId)
    }

    @Test
    fun `onPrimaryClick 为安装动作时会触发安装并刷新升级检查`() = runTest {
        val appManager = FakeAppManager()
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = HomeViewModel(
            appManager = appManager,
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_INSTALL_APP)
        advanceUntilIdle()

        assertEquals(TEST_INSTALL_APP.appId, installManager.installedAppId)
        assertEquals(TEST_INSTALL_APP.appId, upgradeManager.checkedUpgradeAppId)
        assertNull(downloadManager.startedAppId)
    }

    @Test
    fun `onPrimaryClick 为打开动作时会直接打开目标应用`() = runTest {
        val appManager = FakeAppManager()
        val downloadManager = RecordingDownloadManager()
        val installManager = RecordingInstallManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = HomeViewModel(
            appManager = appManager,
            stateCenter = DefaultStateCenter(),
            downloadManager = downloadManager,
            installManager = installManager,
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_OPEN_APP)
        advanceUntilIdle()

        assertEquals(TEST_OPEN_APP.packageName, appManager.openedPackageName)
        assertTrue(appManager.openResult)
        assertNull(downloadManager.startedAppId)
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
        /** 最近一次启动下载的 appId。 */
        var startedAppId: String? = null

        override suspend fun startDownload(appId: String) {
            startedAppId = appId
        }

        override suspend fun pauseDownload(appId: String) = Unit

        override suspend fun resumeDownload(appId: String) = Unit

        override suspend fun cancelDownload(appId: String) = Unit

        override suspend fun removeTask(appId: String, clearFile: Boolean) = Unit

        override suspend fun clearCompletedTasks(): Int = 0

        override suspend fun retryFailedTasks(): Int = 0

        override suspend fun getPreferences() = throw UnsupportedOperationException("not used in test")

        override suspend fun updatePreferences(preferences: com.nio.appstore.data.model.DownloadPreferences) = Unit
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
            appId = "demo.home",
            packageName = "com.nio.demo.home",
            name = "Demo Home",
            description = "demo home app",
            versionName = "1.0.0",
            apkUrl = "https://example.com/demo-home.apk",
        )

        /** 下载动作卡片。 */
        val TEST_DOWNLOAD_APP = AppViewData(
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
        val TEST_INSTALL_APP = AppViewData(
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
        val TEST_OPEN_APP = AppViewData(
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
