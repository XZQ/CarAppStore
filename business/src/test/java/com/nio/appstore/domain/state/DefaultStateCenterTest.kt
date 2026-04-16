package com.nio.appstore.domain.state

import com.nio.appstore.domain.text.BusinessText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStateCenterTest {

    @Test
    fun `snapshot 对未知应用返回默认状态`() {
        val center = DefaultStateCenter()
        val state = center.snapshot("unknown.app")
        assertEquals("unknown.app", state.appId)
        assertEquals(DownloadStatus.IDLE, state.downloadStatus)
        assertEquals(InstallStatus.NOT_INSTALLED, state.installStatus)
        assertEquals(UpgradeStatus.NONE, state.upgradeStatus)
        assertEquals(0, state.progress)
        assertNull(state.localApkPath)
        assertNull(state.installedVersion)
        assertNull(state.errorMessage)
        assertNull(state.errorCode)
    }

    @Test
    fun `updateDownload 正确更新下载维度状态`() {
        val center = DefaultStateCenter()
        center.updateDownload(
            "app.test",
            DownloadStatus.RUNNING,
            progress = 50,
            localApkPath = null,
            errorMessage = null,
            errorCode = null,
        )
        val state = center.snapshot("app.test")
        assertEquals(DownloadStatus.RUNNING, state.downloadStatus)
        assertEquals(50, state.progress)
        assertEquals(PrimaryAction.PAUSE, state.primaryAction)
    }

    @Test
    fun `updateDownload 保留未传入的历史字段`() {
        val center = DefaultStateCenter()
        center.updateInstall("app.test", InstallStatus.INSTALLED, versionName = "1.0.0")
        center.updateDownload("app.test", DownloadStatus.COMPLETED, progress = 100, localApkPath = "/data/test.apk")
        val state = center.snapshot("app.test")
        assertEquals(DownloadStatus.COMPLETED, state.downloadStatus)
        assertEquals(InstallStatus.INSTALLED, state.installStatus)
        assertEquals("1.0.0", state.installedVersion)
        assertEquals("/data/test.apk", state.localApkPath)
    }

    @Test
    fun `updateInstall 正确更新安装维度状态`() {
        val center = DefaultStateCenter()
        center.updateInstall("app.test", InstallStatus.INSTALLING)
        val state = center.snapshot("app.test")
        assertEquals(InstallStatus.INSTALLING, state.installStatus)
        assertEquals(PrimaryAction.DISABLED, state.primaryAction)
    }

    @Test
    fun `updateInstall 成功时记录版本号`() {
        val center = DefaultStateCenter()
        center.updateInstall("app.test", InstallStatus.INSTALLED, versionName = "2.0.0")
        val state = center.snapshot("app.test")
        assertEquals(InstallStatus.INSTALLED, state.installStatus)
        assertEquals("2.0.0", state.installedVersion)
        assertEquals(PrimaryAction.OPEN, state.primaryAction)
    }

    @Test
    fun `updateUpgrade 正确更新升级维度状态`() {
        val center = DefaultStateCenter()
        center.updateInstall("app.test", InstallStatus.INSTALLED, versionName = "1.0.0")
        center.updateUpgrade("app.test", UpgradeStatus.AVAILABLE)
        val state = center.snapshot("app.test")
        assertEquals(UpgradeStatus.AVAILABLE, state.upgradeStatus)
        assertEquals(PrimaryAction.UPGRADE, state.primaryAction)
    }

    @Test
    fun `resetError 清理错误信息`() {
        val center = DefaultStateCenter()
        center.updateDownload("app.test", DownloadStatus.FAILED, errorMessage = "网络超时", errorCode = "TIMEOUT")
        val before = center.snapshot("app.test")
        assertEquals("网络超时", before.errorMessage)
        assertEquals("TIMEOUT", before.errorCode)

        center.resetError("app.test")
        val after = center.snapshot("app.test")
        assertNull(after.errorMessage)
        assertNull(after.errorCode)
    }

    @Test
    fun `syncInstalled 同步安装结果`() {
        val center = DefaultStateCenter()
        center.syncInstalled("app.test", "3.0.0")
        val state = center.snapshot("app.test")
        assertEquals(InstallStatus.INSTALLED, state.installStatus)
        assertEquals("3.0.0", state.installedVersion)
    }

    @Test
    fun `observe 返回的 StateFlow 随更新变化`() = runBlocking {
        val center = DefaultStateCenter()
        val flow = center.observe("app.test")
        assertEquals(DownloadStatus.IDLE, flow.value.downloadStatus)

        center.updateDownload("app.test", DownloadStatus.WAITING)
        assertEquals(DownloadStatus.WAITING, flow.value.downloadStatus)
    }

    @Test
    fun `observeAll 包含所有已操作应用的状态`() {
        val center = DefaultStateCenter()
        center.updateDownload("app.a", DownloadStatus.RUNNING)
        center.updateInstall("app.b", InstallStatus.INSTALLED, versionName = "1.0")

        val all = center.observeAll().value
        assertTrue(all.containsKey("app.a"))
        assertTrue(all.containsKey("app.b"))
        assertEquals(DownloadStatus.RUNNING, all["app.a"]?.downloadStatus)
        assertEquals(InstallStatus.INSTALLED, all["app.b"]?.installStatus)
    }

    @Test
    fun `StateReducer 下载完成时主动作为 INSTALL`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                downloadStatus = DownloadStatus.COMPLETED,
                installStatus = InstallStatus.NOT_INSTALLED,
            )
        )
        assertEquals(PrimaryAction.INSTALL, state.primaryAction)
    }

    @Test
    fun `StateReducer 已安装无升级时主动作为 OPEN`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                installStatus = InstallStatus.INSTALLED,
                upgradeStatus = UpgradeStatus.NONE,
            )
        )
        assertEquals(PrimaryAction.OPEN, state.primaryAction)
    }

    @Test
    fun `StateReducer 已安装有升级时主动作为 UPGRADE`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                installStatus = InstallStatus.INSTALLED,
                upgradeStatus = UpgradeStatus.AVAILABLE,
            )
        )
        assertEquals(PrimaryAction.UPGRADE, state.primaryAction)
    }

    @Test
    fun `StateReducer 下载失败时主动作为 RETRY_DOWNLOAD`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                downloadStatus = DownloadStatus.FAILED,
            )
        )
        assertEquals(PrimaryAction.RETRY_DOWNLOAD, state.primaryAction)
    }

    @Test
    fun `StateReducer 安装失败时主动作为 RETRY_INSTALL`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                installStatus = InstallStatus.FAILED,
            )
        )
        assertEquals(PrimaryAction.RETRY_INSTALL, state.primaryAction)
    }

    @Test
    fun `StateReducer 暂停时主动作为 RESUME`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                downloadStatus = DownloadStatus.PAUSED,
            )
        )
        assertEquals(PrimaryAction.RESUME, state.primaryAction)
    }

    @Test
    fun `StateReducer 升级中时主动作为 DISABLED`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                upgradeStatus = UpgradeStatus.UPGRADING,
            )
        )
        assertEquals(PrimaryAction.DISABLED, state.primaryAction)
    }

    @Test
    fun `StateReducer 等待系统确认时主动作为 DISABLED`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                installStatus = InstallStatus.PENDING_USER_ACTION,
            )
        )
        assertEquals(PrimaryAction.DISABLED, state.primaryAction)
    }

    @Test
    fun `StateReducer 升级失败时主动作为 UPGRADE 允许重试`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                installStatus = InstallStatus.INSTALLED,
                upgradeStatus = UpgradeStatus.FAILED,
                errorMessage = "升级安装失败",
            )
        )
        assertEquals(PrimaryAction.UPGRADE, state.primaryAction)
        assertEquals("升级安装失败", state.statusText)
    }

    @Test
    fun `StateReducer 升级失败文案正确展示`() {
        val state = StateReducer.reduce(
            AppState(
                appId = "test",
                upgradeStatus = UpgradeStatus.FAILED,
                errorMessage = null,
            )
        )
        assertEquals(BusinessText.STATUS_UPGRADE_FAILED, state.statusText)
        assertEquals(PrimaryAction.UPGRADE, state.primaryAction)
    }
}
