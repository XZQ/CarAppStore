package com.nio.appstore.feature.upgrade

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.TaskOverallStatus
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.domain.appmanager.AppManager
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
class UpgradeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load 有任务时会进入内容态`() = runTest {
        val viewModel = UpgradeViewModel(
            appManager = FakeAppManager(),
            stateCenter = DefaultStateCenter(),
            upgradeManager = RecordingUpgradeManager(),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(UpgradeScreenState.Content, viewModel.uiState.value.screenState)
        assertEquals(2, viewModel.uiState.value.availableCount)
    }

    @Test
    fun `onPrimaryClick 为升级动作时会发起升级`() = runTest {
        val appManager = FakeAppManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = UpgradeViewModel(
            appManager = appManager,
            stateCenter = DefaultStateCenter(),
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_UPGRADE_TASK)
        advanceUntilIdle()

        assertEquals(TEST_UPGRADE_TASK.appId, upgradeManager.startedUpgradeAppId)
        assertNull(appManager.openedPackageName)
    }

    @Test
    fun `onPrimaryClick 为打开动作时会打开应用`() = runTest {
        val appManager = FakeAppManager()
        val upgradeManager = RecordingUpgradeManager()
        val viewModel = UpgradeViewModel(
            appManager = appManager,
            stateCenter = DefaultStateCenter(),
            upgradeManager = upgradeManager,
        )

        viewModel.onPrimaryClick(TEST_OPEN_TASK)
        advanceUntilIdle()

        assertEquals(TEST_OPEN_TASK.packageName, appManager.openedPackageName)
        assertNull(upgradeManager.startedUpgradeAppId)
    }

    @Test
    fun `load 失败时会进入错误态`() = runTest {
        val viewModel = UpgradeViewModel(
            appManager = FailingAppManager(),
            stateCenter = DefaultStateCenter(),
            upgradeManager = RecordingUpgradeManager(),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            UpgradeScreenState.Error("upgrade tasks unavailable"),
            viewModel.uiState.value.screenState,
        )
    }

    private open class FakeAppManager : AppManager {
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

        override suspend fun getUpgradeTasks(): List<UpgradeTaskViewData> = listOf(TEST_UPGRADE_TASK, TEST_OPEN_TASK)

        override suspend fun getDownloadTaskStats(): TaskCenterStats = TaskCenterStats()

        override suspend fun getInstallTaskStats(): TaskCenterStats = TaskCenterStats()

        override suspend fun getUpgradeTaskStats(): TaskCenterStats = TaskCenterStats(activeCount = 1, completedCount = 1)

        override fun getPolicyPrompt(): String = ""

        override fun openApp(packageName: String): Boolean {
            openedPackageName = packageName
            return true
        }
    }

    private class FailingAppManager : FakeAppManager() {
        override suspend fun getUpgradeTasks(): List<UpgradeTaskViewData> = error("upgrade tasks unavailable")
    }

    private class RecordingUpgradeManager : UpgradeManager {
        /** 最近一次发起升级的应用。 */
        var startedUpgradeAppId: String? = null

        override suspend fun startUpgrade(appId: String) {
            startedUpgradeAppId = appId
        }

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
            appId = "demo.upgrade.center",
            packageName = "com.nio.demo.upgrade.center",
            name = "Upgrade Center",
            description = "upgrade center test app",
            versionName = "1.0.0",
            apkUrl = "https://example.com/upgrade-center.apk",
        )

        /** 升级动作任务。 */
        val TEST_UPGRADE_TASK = UpgradeTaskViewData(
            appId = "demo.upgrade.task",
            packageName = "com.nio.demo.upgrade.task",
            name = "Upgrade Task",
            currentVersion = "1.0.0",
            targetVersion = "2.0.0",
            stateText = "可升级",
            statusTone = StatusTone.WARNING,
            overallStatus = TaskOverallStatus.PENDING,
            primaryAction = PrimaryAction.UPGRADE,
        )

        /** 打开动作任务。 */
        val TEST_OPEN_TASK = UpgradeTaskViewData(
            appId = "demo.open.task",
            packageName = "com.nio.demo.open.task",
            name = "Open Task",
            currentVersion = "2.0.0",
            targetVersion = "2.0.0",
            stateText = "已升级",
            statusTone = StatusTone.SUCCESS,
            overallStatus = TaskOverallStatus.COMPLETED,
            primaryAction = PrimaryAction.OPEN,
        )
    }
}
