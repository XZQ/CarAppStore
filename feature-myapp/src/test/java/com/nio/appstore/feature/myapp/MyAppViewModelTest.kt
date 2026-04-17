package com.nio.appstore.feature.myapp

import com.nio.appstore.common.ui.StatusTone
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.DefaultStateCenter
import com.nio.appstore.domain.state.PrimaryAction
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

@OptIn(ExperimentalCoroutinesApi::class)
class MyAppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load 有应用时会进入内容态`() = runTest {
        val viewModel = MyAppViewModel(
            appManager = FakeAppManager(apps = listOf(TEST_APP)),
            stateCenter = DefaultStateCenter(),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(MyAppScreenState.Content, viewModel.uiState.value.screenState)
        assertEquals(listOf(TEST_APP), viewModel.uiState.value.apps)
    }

    @Test
    fun `load 失败时会进入错误态`() = runTest {
        val viewModel = MyAppViewModel(
            appManager = FailingAppManager(),
            stateCenter = DefaultStateCenter(),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            MyAppScreenState.Error("my apps unavailable"),
            viewModel.uiState.value.screenState,
        )
    }

    private open class FakeAppManager(
        /** 测试应用列表。 */
        private val apps: List<AppViewData> = emptyList(),
    ) : AppManager {
        override suspend fun getHomeApps(): List<AppViewData> = emptyList()

        override suspend fun getAppDetail(appId: String): AppDetail = TEST_APP_DETAIL

        override suspend fun getMyApps(): List<AppViewData> = apps

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

    private class FailingAppManager : FakeAppManager() {
        override suspend fun getMyApps(): List<AppViewData> = error("my apps unavailable")
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
            appId = "demo.myapp",
            packageName = "com.nio.demo.myapp",
            name = "My App",
            description = "my app detail",
            versionName = "1.0.0",
            apkUrl = "https://example.com/my-app.apk",
        )

        /** 测试应用卡片。 */
        val TEST_APP = AppViewData(
            appId = "demo.myapp.card",
            name = "My App Card",
            description = "my app card",
            versionName = "1.0.0",
            packageName = "com.nio.demo.myapp.card",
            stateText = "已安装",
            statusTone = StatusTone.SUCCESS,
            primaryAction = PrimaryAction.OPEN,
        )
    }
}
