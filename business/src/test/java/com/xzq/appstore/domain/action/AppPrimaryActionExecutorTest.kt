package com.xzq.appstore.domain.action

import com.xzq.appstore.common.ui.StatusTone
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.data.model.AppDetail
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.data.model.DownloadPreferences
import com.xzq.appstore.data.model.DownloadTaskViewData
import com.xzq.appstore.data.model.InstallTaskViewData
import com.xzq.appstore.data.model.TaskCenterStats
import com.xzq.appstore.data.model.UpgradeTaskViewData
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.state.PrimaryAction
import com.xzq.appstore.domain.upgrade.UpgradeBatchResult
import com.xzq.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrimaryActionExecutorTest {
    @Test
    fun `DOWNLOAD 动作会启动下载`() =
        runBlocking {
            val appManager = FakeAppManager()
            val downloadManager = RecordingDownloadManager()
            val installManager = RecordingInstallManager()
            val upgradeManager = RecordingUpgradeManager()
            val executor = AppPrimaryActionExecutor(appManager, downloadManager, installManager, upgradeManager)

            executor.execute(
                appId = TEST_DOWNLOAD_APP.appId,
                action = PrimaryAction.DOWNLOAD,
                packageName = TEST_DOWNLOAD_APP.packageName,
            )

            assertEquals(TEST_DOWNLOAD_APP.appId, downloadManager.startedAppIds.single())
            assertTrue(installManager.installedAppIds.isEmpty())
            assertTrue(upgradeManager.startedUpgradeAppIds.isEmpty())
        }

    @Test
    fun `INSTALL 动作会先安装再检查升级状态`() =
        runBlocking {
            val appManager = FakeAppManager()
            val downloadManager = RecordingDownloadManager()
            val installManager = RecordingInstallManager()
            val upgradeManager = RecordingUpgradeManager()
            val executor = AppPrimaryActionExecutor(appManager, downloadManager, installManager, upgradeManager)

            executor.execute(
                appId = TEST_INSTALL_APP.appId,
                action = PrimaryAction.INSTALL,
                packageName = TEST_INSTALL_APP.packageName,
            )

            assertEquals(TEST_INSTALL_APP.appId, installManager.installedAppIds.single())
            assertEquals(TEST_INSTALL_APP.appId, upgradeManager.checkedUpgradeAppIds.single())
        }

    @Test
    fun `OPEN 动作会使用包名打开应用`() =
        runBlocking {
            val appManager = FakeAppManager()
            val executor =
                AppPrimaryActionExecutor(
                    appManager = appManager,
                    downloadManager = RecordingDownloadManager(),
                    installManager = RecordingInstallManager(),
                    upgradeManager = RecordingUpgradeManager(),
                )

            executor.execute(
                appId = TEST_OPEN_APP.appId,
                action = PrimaryAction.OPEN,
                packageName = TEST_OPEN_APP.packageName,
            )

            assertEquals(TEST_OPEN_APP.packageName, appManager.openedPackageName)
        }

    @Test
    fun `RESUME 动作会恢复下载`() =
        runBlocking {
            val downloadManager = RecordingDownloadManager()
            val executor =
                AppPrimaryActionExecutor(
                    appManager = FakeAppManager(),
                    downloadManager = downloadManager,
                )

            executor.execute(
                appId = TEST_RESUME_APP.appId,
                action = PrimaryAction.RESUME,
                packageName = TEST_RESUME_APP.packageName,
            )

            assertEquals(TEST_RESUME_APP.appId, downloadManager.resumedAppIds.single())
        }

    @Test
    fun `UPGRADE 动作会启动升级`() =
        runBlocking {
            val upgradeManager = RecordingUpgradeManager()
            val executor =
                AppPrimaryActionExecutor(
                    appManager = FakeAppManager(),
                    upgradeManager = upgradeManager,
                )

            executor.execute(
                appId = TEST_UPGRADE_APP.appId,
                action = PrimaryAction.UPGRADE,
                packageName = TEST_UPGRADE_APP.packageName,
            )

            assertEquals(TEST_UPGRADE_APP.appId, upgradeManager.startedUpgradeAppIds.single())
        }

    @Test
    fun `execute records primary click event`() =
        runBlocking {
            val tracker = RecordingTracker()
            val executor =
                AppPrimaryActionExecutor(
                    appManager = FakeAppManager(),
                    downloadManager = RecordingDownloadManager(),
                    tracker = tracker,
                )

            executor.execute(
                appId = TEST_DOWNLOAD_APP.appId,
                action = PrimaryAction.DOWNLOAD,
                packageName = TEST_DOWNLOAD_APP.packageName,
            )

            assertEquals("primary_click_download_${TEST_DOWNLOAD_APP.appId}", tracker.events.single())
        }

    @Test
    fun `DOWNLOAD 抛异常时会把错误向上抛出且不记录副作用`() =
        runBlocking {
            val downloadManager =
                RecordingDownloadManager().apply {
                    errorForStart = IllegalStateException("network down")
                }
            val installManager = RecordingInstallManager()
            val executor =
                AppPrimaryActionExecutor(
                    appManager = FakeAppManager(),
                    downloadManager = downloadManager,
                    installManager = installManager,
                )

            val error =
                try {
                    executor.execute(
                        appId = TEST_DOWNLOAD_APP.appId,
                        action = PrimaryAction.DOWNLOAD,
                        packageName = TEST_DOWNLOAD_APP.packageName,
                    )
                    null
                } catch (e: IllegalStateException) {
                    e
                }

            assertNotNull(error)
            assertEquals("network down", error?.message)
            assertTrue(downloadManager.startedAppIds.isEmpty())
            assertTrue(installManager.installedAppIds.isEmpty())
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
        /** 累计记录所有启动下载请求，便于断言精确调用次数。 */
        val startedAppIds = mutableListOf<String>()

        /** 累计记录所有恢复下载请求，便于断言精确调用次数。 */
        val resumedAppIds = mutableListOf<String>()

        /** 启动下载时按需抛出，便于覆盖错误路径。 */
        var errorForStart: Throwable? = null

        override suspend fun startDownload(appId: String) {
            errorForStart?.let { throw it }
            startedAppIds.add(appId)
        }

        override suspend fun pauseDownload(appId: String) = Unit

        override suspend fun resumeDownload(appId: String) {
            resumedAppIds.add(appId)
        }

        override suspend fun cancelDownload(appId: String) = Unit

        override suspend fun removeTask(
            appId: String,
            clearFile: Boolean,
        ) = Unit

        override suspend fun clearCompletedTasks(): Int = 0

        override suspend fun retryFailedTasks(): Int = 0

        override suspend fun getPreferences(): DownloadPreferences = DownloadPreferences()

        override suspend fun updatePreferences(preferences: DownloadPreferences) = Unit
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

    private class RecordingTracker : EventTracker() {
        val events = mutableListOf<String>()

        override fun track(event: String) {
            events += event
        }
    }

    private companion object {
        /** 测试详情模型。 */
        val TEST_APP_DETAIL =
            AppDetail(
                appId = "demo.executor",
                packageName = "com.nio.demo.executor",
                name = "Demo Executor",
                description = "executor test app",
                versionName = "1.0.0",
                apkUrl = "https://example.com/demo-executor.apk",
            )

        /** 下载动作卡片。 */
        val TEST_DOWNLOAD_APP =
            AppViewData(
                appId = "demo.executor.download",
                name = "Download App",
                description = "download app",
                versionName = "1.0.0",
                packageName = "com.nio.demo.executor.download",
                stateText = "待下载",
                statusTone = StatusTone.NEUTRAL,
                primaryAction = PrimaryAction.DOWNLOAD,
            )

        /** 安装动作卡片。 */
        val TEST_INSTALL_APP =
            AppViewData(
                appId = "demo.executor.install",
                name = "Install App",
                description = "install app",
                versionName = "1.0.1",
                packageName = "com.nio.demo.executor.install",
                stateText = "下载完成",
                statusTone = StatusTone.WARNING,
                primaryAction = PrimaryAction.INSTALL,
            )

        /** 打开动作卡片。 */
        val TEST_OPEN_APP =
            AppViewData(
                appId = "demo.executor.open",
                name = "Open App",
                description = "open app",
                versionName = "1.0.2",
                packageName = "com.nio.demo.executor.open",
                stateText = "已安装",
                statusTone = StatusTone.SUCCESS,
                primaryAction = PrimaryAction.OPEN,
                installed = true,
            )

        /** 恢复动作卡片。 */
        val TEST_RESUME_APP =
            AppViewData(
                appId = "demo.executor.resume",
                name = "Resume App",
                description = "resume app",
                versionName = "1.0.3",
                packageName = "com.nio.demo.executor.resume",
                stateText = "已暂停",
                statusTone = StatusTone.WARNING,
                primaryAction = PrimaryAction.RESUME,
            )

        /** 升级动作卡片。 */
        val TEST_UPGRADE_APP =
            AppViewData(
                appId = "demo.executor.upgrade",
                name = "Upgrade App",
                description = "upgrade app",
                versionName = "2.0.0",
                packageName = "com.nio.demo.executor.upgrade",
                stateText = "可升级",
                statusTone = StatusTone.WARNING,
                primaryAction = PrimaryAction.UPGRADE,
                installed = true,
            )
    }
}
