package com.nio.appstore.feature.installcenter

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.SessionBucket
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.TaskOverallStatus
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.install.InstallManager
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class InstallCenterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load 有任务时会进入内容态`() = runTest {
        val viewModel = InstallCenterViewModel(
            appManager = FakeAppManager(),
            stateCenter = DefaultStateCenter(),
            installManager = RecordingInstallManager(),
            upgradeManager = RecordingUpgradeManager(),
            installSessionStore = InstallSessionStore(Files.createTempFile("install-center-load", ".json").toFile()),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(InstallCenterScreenState.Content, viewModel.uiState.value.screenState)
        assertEquals(1, viewModel.uiState.value.tasks.size)
    }

    @Test
    fun `onPrimaryClick 为重试安装动作时会发起安装`() = runTest {
        val installManager = RecordingInstallManager()
        val viewModel = InstallCenterViewModel(
            appManager = FakeAppManager(),
            stateCenter = DefaultStateCenter(),
            installManager = installManager,
            upgradeManager = RecordingUpgradeManager(),
            installSessionStore = InstallSessionStore(Files.createTempFile("install-center", ".json").toFile()),
        )

        viewModel.onPrimaryClick(TEST_RETRY_INSTALL_TASK.appId, TEST_RETRY_INSTALL_TASK.primaryAction)
        advanceUntilIdle()

        assertEquals(TEST_RETRY_INSTALL_TASK.appId, installManager.installedAppId)
    }

    @Test
    fun `load 失败时会进入错误态`() = runTest {
        val viewModel = InstallCenterViewModel(
            appManager = FailingAppManager(),
            stateCenter = DefaultStateCenter(),
            installManager = RecordingInstallManager(),
            upgradeManager = RecordingUpgradeManager(),
            installSessionStore = InstallSessionStore(Files.createTempFile("install-center-fail", ".json").toFile()),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            InstallCenterScreenState.Error("install tasks unavailable"),
            viewModel.uiState.value.screenState,
        )
    }

    private open class FakeAppManager : AppManager {
        override suspend fun getHomeApps(): List<AppViewData> = emptyList()

        override suspend fun getAppDetail(appId: String): AppDetail = TEST_APP_DETAIL

        override suspend fun getMyApps(): List<AppViewData> = emptyList()

        override suspend fun getHomeAppViewData(appId: String): AppViewData? = null

        override suspend fun searchApps(keyword: String): List<AppViewData> = emptyList()

        override suspend fun getDownloadManageApps(): List<AppViewData> = emptyList()

        override suspend fun getDownloadTasks(): List<DownloadTaskViewData> = emptyList()

        override suspend fun getUpgradeManageApps(): List<AppViewData> = emptyList()

        override suspend fun getInstallTasks(): List<InstallTaskViewData> = listOf(TEST_RETRY_INSTALL_TASK)

        override suspend fun getUpgradeTasks(): List<UpgradeTaskViewData> = emptyList()

        override suspend fun getDownloadTaskStats(): TaskCenterStats = TaskCenterStats()

        override suspend fun getInstallTaskStats(): TaskCenterStats = TaskCenterStats(failedCount = 1)

        override suspend fun getUpgradeTaskStats(): TaskCenterStats = TaskCenterStats()

        override fun getPolicyPrompt(): String = ""

        override fun openApp(packageName: String): Boolean = true
    }

    private class FailingAppManager : FakeAppManager() {
        override suspend fun getInstallTasks(): List<InstallTaskViewData> = error("install tasks unavailable")
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

        override suspend fun checkAllUpgrades(): List<String> = emptyList()

        override suspend fun startBatchUpgrade(appIds: List<String>) = Unit
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
            appId = "demo.install.center",
            packageName = "com.nio.demo.install.center",
            name = "Install Center",
            description = "install center test app",
            versionName = "1.0.0",
            apkUrl = "https://example.com/install-center.apk",
        )

        /** 可重试安装任务。 */
        val TEST_RETRY_INSTALL_TASK = InstallTaskViewData(
            appId = "demo.retry.install",
            packageName = "com.nio.demo.retry.install",
            name = "Retry Install",
            versionName = "1.0.1",
            stateText = "安装失败",
            statusTone = StatusTone.ERROR,
            overallStatus = TaskOverallStatus.FAILED,
            primaryAction = PrimaryAction.RETRY_INSTALL,
            reasonText = "安装中断",
            sessionBucket = SessionBucket.FAILED,
        )
    }
}
