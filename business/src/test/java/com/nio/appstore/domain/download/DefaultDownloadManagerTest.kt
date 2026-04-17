package com.nio.appstore.domain.download

import com.nio.appstore.core.downloader.DownloadEvent
import com.nio.appstore.core.downloader.DownloadExecutionControl
import com.nio.appstore.core.downloader.DownloadFailureCode
import com.nio.appstore.core.downloader.DownloadRemoteMeta
import com.nio.appstore.core.downloader.DownloadRequest
import com.nio.appstore.core.downloader.FileDownloader
import com.nio.appstore.core.logger.AppLogger
import com.nio.appstore.core.tracker.EventTracker
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppInfo
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadSegmentRecord
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.data.model.InstalledApp
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.data.model.UpgradeInfo
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.policy.PolicyResult
import com.nio.appstore.domain.state.DefaultStateCenter
import com.nio.appstore.domain.state.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DefaultDownloadManagerTest {

    @Test
    fun `startDownload 重复触发时只会启动一个活动任务`() = runBlocking {
        val harness = TestHarness()

        harness.manager.startDownload(TEST_APP_ID)
        harness.manager.startDownload(TEST_APP_ID)

        assertTrue(harness.downloader.startedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        waitUntil { harness.downloader.startCount.get() == 1 }

        harness.manager.cancelDownload(TEST_APP_ID)
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.CANCELED
        }

        assertEquals(1, harness.downloader.startCount.get())
    }

    @Test
    fun `pauseDownload 会让底层下载停止并保留已下载进度`() = runBlocking {
        val harness = TestHarness()

        harness.manager.startDownload(TEST_APP_ID)
        assertTrue(harness.downloader.startedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.RUNNING
        }

        harness.manager.pauseDownload(TEST_APP_ID)
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.PAUSED
        }

        val task = requireNotNull(harness.repository.getDownloadTask(TEST_APP_ID))
        assertEquals(DownloadStatus.PAUSED, task.status)
        assertEquals(TEST_DOWNLOADED_BYTES.toInt() * 100 / TEST_TOTAL_BYTES.toInt(), task.progress)
        assertEquals(TEST_DOWNLOADED_BYTES, task.downloadedBytes)
        assertEquals(DownloadStatus.PAUSED, harness.stateCenter.snapshot(TEST_APP_ID).downloadStatus)
    }

    @Test
    fun `cancelDownload 会让底层下载停止并清空本地产物引用`() = runBlocking {
        val harness = TestHarness()

        harness.repository.saveDownloadSegments(
            TEST_APP_ID,
            listOf(
                DownloadSegmentRecord(
                    segmentId = "segment-1",
                    taskId = "download-$TEST_APP_ID",
                    index = 0,
                    startByte = 0L,
                    endByte = TEST_TOTAL_BYTES - 1L,
                    downloadedBytes = TEST_DOWNLOADED_BYTES,
                    status = "RUNNING",
                    tmpFilePath = File(harness.workDir, "segment-1.tmp").absolutePath,
                    retryCount = 0,
                    createdAt = 1L,
                    updatedAt = 1L,
                )
            )
        )
        harness.repository.saveDownloadedApk(TEST_APP_ID, File(harness.workDir, "stale.apk").absolutePath)

        harness.manager.startDownload(TEST_APP_ID)
        assertTrue(harness.downloader.startedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.RUNNING
        }

        harness.manager.cancelDownload(TEST_APP_ID)
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.CANCELED
        }

        val task = requireNotNull(harness.repository.getDownloadTask(TEST_APP_ID))
        assertEquals(DownloadStatus.CANCELED, task.status)
        assertEquals(0, task.progress)
        assertEquals(DownloadFailureCode.USER_CANCELED.name, task.failureCode)
        assertTrue(harness.repository.getDownloadSegments(TEST_APP_ID).isEmpty())
        assertNull(harness.repository.getDownloadedApk(TEST_APP_ID))
    }

    @Test
    fun `resumeDownload 会基于已保存进度继续下载直到完成`() = runBlocking {
        val harness = TestHarness()

        harness.manager.startDownload(TEST_APP_ID)
        assertTrue(harness.downloader.startedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.RUNNING
        }

        harness.manager.pauseDownload(TEST_APP_ID)
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.PAUSED
        }

        harness.manager.resumeDownload(TEST_APP_ID)
        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.COMPLETED
        }

        val task = requireNotNull(harness.repository.getDownloadTask(TEST_APP_ID))
        assertEquals(DownloadStatus.COMPLETED, task.status)
        assertEquals(100, task.progress)
        assertEquals(TEST_TOTAL_BYTES, task.downloadedBytes)
        assertEquals(2, harness.downloader.startCount.get())
        assertNotNull(harness.repository.getDownloadedApk(TEST_APP_ID))
        assertEquals(DownloadStatus.COMPLETED, harness.stateCenter.snapshot(TEST_APP_ID).downloadStatus)
    }

    @Test
    fun `冷启动恢复时会把运行中任务归一化为暂停且不自动启动`() = runBlocking {
        val harness = TestHarness(
            configureRepository = {
                saveDownloadPreferences(
                    DownloadPreferences(
                        autoResumeOnLaunch = false,
                        autoRetryEnabled = true,
                        maxAutoRetryCount = 2,
                    )
                )
                val targetFile = createPartialDownloadFileForTest()
                saveDownloadTask(
                    buildDownloadTaskRecord(
                        status = DownloadStatus.RUNNING,
                        progress = 1,
                        targetFilePath = targetFile.absolutePath,
                        downloadedBytes = 1L,
                        totalBytes = TEST_TOTAL_BYTES,
                        retryCount = 0,
                    )
                )
            }
        )

        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.PAUSED
        }

        val task = requireNotNull(harness.repository.getDownloadTask(TEST_APP_ID))
        assertEquals(DownloadStatus.PAUSED, task.status)
        assertEquals(TEST_DOWNLOADED_BYTES, task.downloadedBytes)
        assertEquals(TEST_DOWNLOADED_BYTES.toInt() * 100 / TEST_TOTAL_BYTES.toInt(), task.progress)
        assertEquals(0, harness.downloader.startCount.get())
        assertEquals(DownloadStatus.PAUSED, harness.stateCenter.snapshot(TEST_APP_ID).downloadStatus)
    }

    @Test
    fun `冷启动恢复时会在开启自动恢复后继续执行暂停任务`() = runBlocking {
        val harness = TestHarness(
            configureRepository = {
                saveDownloadPreferences(
                    DownloadPreferences(
                        autoResumeOnLaunch = true,
                        autoRetryEnabled = true,
                        maxAutoRetryCount = 2,
                    )
                )
                val targetFile = createPartialDownloadFileForTest()
                saveDownloadTask(
                    buildDownloadTaskRecord(
                        status = DownloadStatus.PAUSED,
                        progress = 40,
                        targetFilePath = targetFile.absolutePath,
                        downloadedBytes = TEST_DOWNLOADED_BYTES,
                        totalBytes = TEST_TOTAL_BYTES,
                        retryCount = 0,
                    )
                )
            }
        )

        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.COMPLETED
        }

        val task = requireNotNull(harness.repository.getDownloadTask(TEST_APP_ID))
        assertEquals(1, harness.downloader.startCount.get())
        assertEquals(DownloadStatus.COMPLETED, task.status)
        assertEquals(100, task.progress)
    }

    @Test
    fun `冷启动恢复时会在开启自动重试后重试失败任务`() = runBlocking {
        val harness = TestHarness(
            configureRepository = {
                saveDownloadPreferences(
                    DownloadPreferences(
                        autoResumeOnLaunch = false,
                        autoRetryEnabled = true,
                        maxAutoRetryCount = 2,
                    )
                )
                val targetFile = createPartialDownloadFileForTest()
                saveDownloadTask(
                    buildDownloadTaskRecord(
                        status = DownloadStatus.FAILED,
                        progress = 40,
                        targetFilePath = targetFile.absolutePath,
                        downloadedBytes = TEST_DOWNLOADED_BYTES,
                        totalBytes = TEST_TOTAL_BYTES,
                        retryCount = 1,
                    )
                )
            }
        )

        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.COMPLETED
        }

        val task = requireNotNull(harness.repository.getDownloadTask(TEST_APP_ID))
        assertEquals(1, harness.downloader.startCount.get())
        assertEquals(DownloadStatus.COMPLETED, task.status)
        assertEquals(100, task.progress)
    }

    @Test
    fun `冷启动恢复时会把丢失 APK 的已完成任务纠正为失败态`() = runBlocking {
        val harness = TestHarness(
            configureRepository = {
                saveDownloadPreferences(
                    DownloadPreferences(
                        autoResumeOnLaunch = false,
                        autoRetryEnabled = false,
                        maxAutoRetryCount = 2,
                    )
                )
                val missingTargetFile = File(createMissingDownloadFilePathForTest())
                saveDownloadedApk(TEST_APP_ID, missingTargetFile.absolutePath)
                saveDownloadTask(
                    buildDownloadTaskRecord(
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        targetFilePath = missingTargetFile.absolutePath,
                        downloadedBytes = TEST_TOTAL_BYTES,
                        totalBytes = TEST_TOTAL_BYTES,
                        retryCount = 0,
                    )
                )
            }
        )

        waitUntil {
            harness.repository.getDownloadTask(TEST_APP_ID)?.status == DownloadStatus.FAILED
        }

        val task = requireNotNull(harness.repository.getDownloadTask(TEST_APP_ID))
        assertEquals(0, harness.downloader.startCount.get())
        assertEquals(DownloadStatus.FAILED, task.status)
        assertEquals(DownloadFailureCode.FILE_MISSING.name, task.failureCode)
        assertEquals(DownloadStatus.FAILED, harness.stateCenter.snapshot(TEST_APP_ID).downloadStatus)
    }

    /** 等待条件成立，避免后台下载协程的异步写回造成断言竞争。 */
    private suspend fun waitUntil(predicate: suspend () -> Boolean) {
        repeat(MAX_WAIT_RETRY_COUNT) {
            if (predicate()) return
            delay(POLL_DELAY_MS)
        }
        throw AssertionError("condition not satisfied within timeout")
    }

    /** 创建默认的下载任务记录，供不同恢复场景复用。 */
    private fun buildDownloadTaskRecord(
        status: DownloadStatus,
        progress: Int,
        targetFilePath: String,
        downloadedBytes: Long,
        totalBytes: Long,
        retryCount: Int,
    ): DownloadTaskRecord {
        return DownloadTaskRecord(
            taskId = "download-$TEST_APP_ID",
            appId = TEST_APP_ID,
            status = status,
            progress = progress,
            targetFilePath = targetFilePath,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            failureCode = if (status == DownloadStatus.FAILED) DownloadFailureCode.NETWORK_INTERRUPTED.name else null,
            failureMessage = if (status == DownloadStatus.FAILED) DownloadFailureCode.NETWORK_INTERRUPTED.displayText else null,
            retryCount = retryCount,
            downloadUrl = "https://example.com/test.apk",
            tempDirPath = null,
            eTag = null,
            lastModified = null,
            supportsRange = true,
            checksumType = null,
            checksumValue = null,
            segmentCount = 2,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    /** 测试下载管理器时使用的依赖集合。 */
    private class TestHarness(
        /** 初始化下载管理器前预置仓库状态。 */
        configureRepository: suspend FakeRepository.() -> Unit = {},
    ) {
        /** 每个测试对应的临时工作目录。 */
        val workDir: File = Files.createTempDirectory("download-manager-test").toFile()

        /** 供下载管理器读写持久化状态的仓库替身。 */
        val repository = FakeRepository(workDir)

        /** 当前测试使用的状态中心实现。 */
        val stateCenter = DefaultStateCenter()

        /** 可控的下载器替身，用于模拟运行中、暂停和取消。 */
        val downloader = ControllableFileDownloader()

        /** 被测下载管理器实例。 */
        val manager: DefaultDownloadManager

        init {
            runBlocking {
                repository.configureRepository()
            }
            manager = DefaultDownloadManager(
                repository = repository,
                stateCenter = stateCenter,
                policyCenter = AllowAllPolicyCenter(),
                fileDownloader = downloader,
                logger = QuietLogger(),
                tracker = QuietTracker(),
            )
        }
    }

    /** 允许所有下载动作通过的策略中心替身。 */
    private class AllowAllPolicyCenter : PolicyCenter {
        /** 测试策略流。 */
        private val settingsFlow = MutableStateFlow(PolicySettings())

        override fun canDownload(appId: String): PolicyResult = PolicyResult(allow = true)

        override fun canInstall(appId: String): PolicyResult = PolicyResult(allow = true)

        override fun canUpgrade(appId: String): PolicyResult = PolicyResult(allow = true)

        override fun observeSettings() = settingsFlow

        override fun getSettings(): PolicySettings = settingsFlow.value

        override fun getStoredSettings(): PolicySettings = settingsFlow.value

        override fun updateSettings(settings: PolicySettings) {
            settingsFlow.value = settings
        }
    }

    /** 静默日志器，避免 JVM 单测中触发 Android Log。 */
    private class QuietLogger : AppLogger() {
        override fun d(tag: String, message: String) = Unit
    }

    /** 静默打点器，避免 JVM 单测中触发 Android Log。 */
    private class QuietTracker : EventTracker() {
        override fun track(event: String) = Unit
    }

    /** 可控下载器，用来模拟长时间运行并响应暂停/取消指令。 */
    private class ControllableFileDownloader : FileDownloader {
        /** 实际启动下载流程的次数。 */
        val startCount = AtomicInteger(0)

        /** 首次进入下载流程时发出的信号。 */
        val startedLatch = CountDownLatch(1)

        override suspend fun download(
            request: DownloadRequest,
            control: DownloadExecutionControl,
            onEvent: suspend (DownloadEvent) -> Unit,
        ) {
            startCount.incrementAndGet()
            startedLatch.countDown()
            val resumedBytes = if (request.downloadedBytes >= TEST_DOWNLOADED_BYTES) {
                TEST_TOTAL_BYTES
            } else {
                TEST_DOWNLOADED_BYTES
            }
            onEvent(DownloadEvent.Waiting)
            onEvent(
                DownloadEvent.MetaReady(
                    DownloadRemoteMeta(
                        contentLength = TEST_TOTAL_BYTES,
                        supportsRange = true,
                    )
                )
            )
            onEvent(
                DownloadEvent.Running(
                    downloadedBytes = resumedBytes,
                    totalBytes = TEST_TOTAL_BYTES,
                    speedBytesPerSec = TEST_SPEED_BYTES_PER_SECOND,
                )
            )

            if (resumedBytes == TEST_TOTAL_BYTES) {
                onEvent(
                    DownloadEvent.Completed(
                        file = request.targetFile,
                        totalBytes = TEST_TOTAL_BYTES,
                    )
                )
                return
            }

            while (true) {
                val stopReason = control.currentStopReason()
                if (stopReason != null) {
                    onEvent(
                        DownloadEvent.Stopped(
                            reason = stopReason,
                            downloadedBytes = resumedBytes,
                            totalBytes = TEST_TOTAL_BYTES,
                        )
                    )
                    return
                }
                delay(POLL_DELAY_MS)
            }
        }
    }

    /** 仅覆盖当前测试所需行为的仓库替身。 */
    private class FakeRepository(
        /** 当前测试专用的工作目录。 */
        private val workDir: File,
    ) : AppRepository {
        /** 当前测试应用详情。 */
        private val detail = AppDetail(
            appId = TEST_APP_ID,
            packageName = "com.nio.test",
            name = "Test App",
            description = "download manager test",
            versionName = "1.0.0",
            apkUrl = "https://example.com/test.apk",
        )

        /** 下载任务记录表。 */
        private val downloadTasks = linkedMapOf<String, DownloadTaskRecord>()

        /** 下载分片记录表。 */
        private val downloadSegments = linkedMapOf<String, List<DownloadSegmentRecord>>()

        /** 已缓存 APK 路径表。 */
        private val downloadedApkPaths = linkedMapOf<String, String>()

        /** staged 升级目标版本表。 */
        private val stagedUpgradeVersions = linkedMapOf<String, String>()

        /** 下载偏好配置。 */
        private var downloadPreferences = DownloadPreferences()

        override suspend fun getHomeApps(): List<AppInfo> = emptyList()

        override suspend fun getAppDetail(appId: String): AppDetail = detail

        override suspend fun getInstalledApps(): List<InstalledApp> = emptyList()

        override suspend fun markInstalled(appId: String) = Unit

        override suspend fun isInstalled(appId: String): Boolean = false

        override suspend fun saveDownloadedApk(appId: String, apkPath: String) {
            downloadedApkPaths[appId] = apkPath
        }

        override suspend fun getDownloadedApk(appId: String): String? = downloadedApkPaths[appId]

        override suspend fun clearDownloadedApk(appId: String) {
            downloadedApkPaths.remove(appId)
        }

        override suspend fun getUpgradeInfo(appId: String): UpgradeInfo {
            return UpgradeInfo(
                appId = appId,
                latestVersion = "1.0.1",
                apkUrl = detail.apkUrl,
                hasUpgrade = true,
            )
        }

        override suspend fun stageUpgrade(appId: String, versionName: String) {
            stagedUpgradeVersions[appId] = versionName
        }

        override suspend fun peekStagedUpgradeVersion(appId: String): String? = stagedUpgradeVersions[appId]

        override suspend fun saveDownloadTask(record: DownloadTaskRecord) {
            downloadTasks[record.appId] = record
        }

        override suspend fun getDownloadTask(appId: String): DownloadTaskRecord? = downloadTasks[appId]

        override suspend fun getAllDownloadTasks(): List<DownloadTaskRecord> = downloadTasks.values.toList()

        override suspend fun removeDownloadTask(appId: String) {
            downloadTasks.remove(appId)
        }

        override suspend fun clearCompletedDownloadTasks(): Int {
            val removableKeys = downloadTasks.values
                .filter { it.status == DownloadStatus.COMPLETED }
                .map { it.appId }
            removableKeys.forEach { downloadTasks.remove(it) }
            return removableKeys.size
        }

        override suspend fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) {
            downloadSegments[appId] = segments
        }

        override suspend fun getDownloadSegments(appId: String): List<DownloadSegmentRecord> {
            return downloadSegments[appId].orEmpty()
        }

        override suspend fun getOrCreateDownloadFile(appId: String): File {
            val downloadsDir = File(workDir, "downloads").apply { mkdirs() }
            return File(downloadsDir, "$appId.apk")
        }

        /** 创建一份用于恢复测试的半下载 APK 文件。 */
        suspend fun createPartialDownloadFileForTest(): File {
            return getOrCreateDownloadFile(TEST_APP_ID).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(TEST_DOWNLOADED_BYTES.toInt()) { 1 })
            }
        }

        /** 创建一条不存在的 APK 路径，用于模拟失效的完成记录。 */
        fun createMissingDownloadFilePathForTest(): String {
            return File(workDir, "downloads/missing-$TEST_APP_ID.apk").absolutePath
        }

        override suspend fun getDownloadPreferences(): DownloadPreferences = downloadPreferences

        override suspend fun saveDownloadPreferences(preferences: DownloadPreferences) {
            downloadPreferences = preferences
        }

        override fun getPolicySettings(): PolicySettings = PolicySettings()

        override fun openApp(packageName: String): Boolean = false
    }

    private companion object {
        /** 测试应用统一使用的 appId。 */
        const val TEST_APP_ID = "demo.app"

        /** 模拟下载总字节数。 */
        const val TEST_TOTAL_BYTES = 1_000L

        /** 模拟已下载字节数。 */
        const val TEST_DOWNLOADED_BYTES = 400L

        /** 模拟下载速度。 */
        const val TEST_SPEED_BYTES_PER_SECOND = 128L

        /** 等待异步状态收敛时的单次轮询间隔。 */
        const val POLL_DELAY_MS = 20L

        /** 等待异步条件时允许的最大轮询次数。 */
        const val MAX_WAIT_RETRY_COUNT = 100

        /** CountDownLatch 等待超时时间。 */
        const val TIMEOUT_SECONDS = 3L
    }
}
