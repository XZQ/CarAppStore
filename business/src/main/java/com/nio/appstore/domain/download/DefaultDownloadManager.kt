package com.nio.appstore.domain.download

import com.nio.appstore.core.downloader.DownloadEvent
import com.nio.appstore.core.downloader.DownloadExecutionControl
import com.nio.appstore.core.downloader.DownloadFailureCode
import com.nio.appstore.core.downloader.DownloadRequest
import com.nio.appstore.core.downloader.DownloadStopReason
import com.nio.appstore.core.downloader.FileDownloader
import com.nio.appstore.core.logger.AppLogger
import com.nio.appstore.core.tracker.EventTracker
import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.InstallStatus
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.text.BusinessText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class DefaultDownloadManager(
    /** 统一数据入口，负责下载任务、分片和 APK 路径持久化。 */
    private val repository: AppRepository,
    /** 运行态状态中心，负责向页面同步下载状态。 */
    private val stateCenter: StateCenter,
    /** 下载前置策略中心，负责 Wi‑Fi、存储空间等限制判断。 */
    private val policyCenter: PolicyCenter,
    /** 真正执行文件下载的底层实现。 */
    private val fileDownloader: FileDownloader,
    /** 下载链路日志器。 */
    private val logger: AppLogger,
    /** 下载链路打点器。 */
    private val tracker: EventTracker,
) : DownloadManager {
    /** 用于冷启动恢复下载任务的后台协程作用域。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 保护活动下载任务注册表的并发访问。 */
    private val executionMutex = Mutex()

    /** 当前仍在执行中的下载任务。 */
    private val activeExecutions = mutableMapOf<String, ActiveDownloadExecution>()

    /** 活动下载任务在业务层保存的运行句柄。 */
    private data class ActiveDownloadExecution(
        /** 控制底层下载停止的控制器。 */
        val control: DownloadExecutionControl,
        /** 当前下载任务对应的后台协程。 */
        val job: Job,
    )

    init {
        // 下载管理器初始化时立即恢复上次持久化任务，保证冷启动后状态连续。
        scope.launch {
            restorePersistedTasks()
        }
    }

    /**
     * 启动指定应用的下载流程。
     *
     * 当前实现会先注册活动任务，再在后台协程中执行真正的下载流程。
     */
    override suspend fun startDownload(appId: String) {
        val control = DownloadExecutionControl()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                executeDownload(appId, control)
            } finally {
                unregisterExecution(appId, control)
            }
        }
        val execution = ActiveDownloadExecution(control = control, job = job)
        val registered = registerExecution(appId, execution)
        if (!registered) {
            job.cancel()
            logger.d("DownloadManager", "ignore duplicate start: $appId")
            return
        }
        stateCenter.resetError(appId)
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.WAITING,
            progress = repository.getDownloadTask(appId)?.progress ?: stateCenter.snapshot(appId).progress,
            localApkPath = null,
            errorMessage = null,
            errorCode = null,
        )
        job.start()
    }

    /** 将指定下载任务切换为暂停状态。 */
    override suspend fun pauseDownload(appId: String) {
        val execution = getActiveExecution(appId)
        if (execution != null) {
            execution.control.requestPause()
            val currentRecord = repository.getDownloadTask(appId)
            val progress = currentRecord?.progress ?: stateCenter.snapshot(appId).progress
            stateCenter.updateDownload(appId, DownloadStatus.PAUSED, progress = progress)
            return
        }
        val record = repository.getDownloadTask(appId) ?: return
        saveRecord(
            record.copy(
                status = DownloadStatus.PAUSED,
                speedBytesPerSec = 0L,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        stateCenter.updateDownload(appId, DownloadStatus.PAUSED, progress = record.progress)
    }

    /** 恢复处于暂停、失败或取消状态的下载任务。 */
    override suspend fun resumeDownload(appId: String) {
        val execution = getActiveExecution(appId)
        if (execution != null) {
            // 用户在暂停刚落盘后立即恢复时，先等待旧协程彻底收尾，避免被活动任务注册表误判为重复启动。
            if (execution.control.currentStopReason() == DownloadStopReason.PAUSED) {
                execution.job.join()
            } else {
                return
            }
        }
        val record = repository.getDownloadTask(appId)
        if (record == null || record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.FAILED || record.status == DownloadStatus.CANCELED) {
            startDownload(appId)
        }
    }

    /** 取消下载任务，并清理 APK 路径与分片信息。 */
    override suspend fun cancelDownload(appId: String) {
        val execution = getActiveExecution(appId)
        if (execution != null) {
            execution.control.requestCancel()
            stateCenter.updateDownload(
                appId = appId,
                status = DownloadStatus.CANCELED,
                progress = 0,
                localApkPath = null,
                errorMessage = DownloadFailureCode.USER_CANCELED.displayText,
                errorCode = DownloadFailureCode.USER_CANCELED.name,
            )
            return
        }
        val record = repository.getDownloadTask(appId) ?: return
        // 取消语义要求本地产物和分片缓存都失效，避免后续误用旧文件。
        repository.clearDownloadedApk(appId)
        repository.saveDownloadSegments(appId, emptyList())
        saveRecord(
            record.copy(
                status = DownloadStatus.CANCELED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBytesPerSec = 0L,
                failureCode = DownloadFailureCode.USER_CANCELED.name,
                failureMessage = DownloadFailureCode.USER_CANCELED.displayText,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.CANCELED,
            progress = 0,
            localApkPath = null,
            errorMessage = DownloadFailureCode.USER_CANCELED.displayText,
            errorCode = DownloadFailureCode.USER_CANCELED.name,
        )
    }

    /** 删除下载任务，并根据参数决定是否一起删除本地文件。 */
    override suspend fun removeTask(appId: String, clearFile: Boolean) {
        val snapshot = stateCenter.snapshot(appId)
        if (clearFile) {
            repository.clearDownloadedApk(appId)
        }
        repository.saveDownloadSegments(appId, emptyList())
        repository.removeDownloadTask(appId)
        // 删除任务后统一把下载运行态复位，避免页面继续展示孤立状态。
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.IDLE,
            progress = 0,
            localApkPath = null,
            errorMessage = null,
            errorCode = null,
        )
        if (clearFile && snapshot.installStatus != InstallStatus.INSTALLED) {
            // 如果 APK 也被删掉了且目标应用没有安装，需要把安装态一起复位。
            stateCenter.updateInstall(
                appId = appId,
                status = InstallStatus.NOT_INSTALLED,
                versionName = null,
                errorMessage = null,
                errorCode = null,
            )
        }
    }

    /** 清理所有已完成或已取消的下载任务。 */
    override suspend fun clearCompletedTasks(): Int {
        val completedTasks = repository.getAllDownloadTasks().filter {
            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELED
        }
        completedTasks.forEach { removeTask(it.appId, clearFile = true) }
        return completedTasks.size
    }

    /** 重试所有失败或已取消的下载任务。 */
    override suspend fun retryFailedTasks(): Int {
        val failedTasks = repository.getAllDownloadTasks().filter {
            it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED
        }
        failedTasks.forEach { startDownload(it.appId) }
        return failedTasks.size
    }

    /** 读取下载偏好配置。 */
    override suspend fun getPreferences(): DownloadPreferences = repository.getDownloadPreferences()

    /** 持久化新的下载偏好配置。 */
    override suspend fun updatePreferences(preferences: DownloadPreferences) {
        repository.saveDownloadPreferences(preferences)
    }

    /** 在后台协程中真正执行一次下载流程。 */
    private suspend fun executeDownload(appId: String, control: DownloadExecutionControl) {
        // 在真正开始下载前先做策略判断，避免不允许下载时还创建无效任务。
        val policy = policyCenter.canDownload(appId)
        if (!policy.allow) {
            markFailed(
                appId = appId,
                record = repository.getDownloadTask(appId),
                errorCode = DownloadFailureCode.UNKNOWN.name,
                errorMessage = BusinessText.downloadRestricted(policy.reason),
            )
            return
        }

        // 读取应用详情和目标文件路径，并基于历史任务记录准备新的下载快照。
        val detail = repository.getAppDetail(appId)
        val targetFile = repository.getOrCreateDownloadFile(appId)
        val current = repository.getDownloadTask(appId)
        val now = System.currentTimeMillis()
        val prepared = (current ?: newRecord(appId, detail, targetFile.absolutePath, now)).copy(
            status = DownloadStatus.WAITING,
            targetFilePath = targetFile.absolutePath,
            downloadUrl = detail.apkUrl,
            checksumType = detail.checksumType,
            checksumValue = detail.checksumValue,
            updatedAt = now,
            failureCode = null,
            failureMessage = null,
        )

        // 下载器启动前先把等待态写入持久化和状态中心，保证页面立即看到任务。
        repository.saveDownloadTask(prepared)
        stateCenter.resetError(appId)
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.WAITING,
            progress = prepared.progress,
            localApkPath = null,
            errorMessage = null,
            errorCode = null,
        )
        tracker.track("download_start_$appId")

        try {
            // 将业务层任务记录转换为底层下载请求，并逐个消费下载事件。
            fileDownloader.download(
                request = DownloadRequest(
                    taskId = prepared.taskId,
                    appId = appId,
                    url = detail.apkUrl,
                    targetFile = targetFile,
                    downloadedBytes = prepared.downloadedBytes,
                    totalBytes = prepared.totalBytes,
                    attempt = prepared.retryCount,
                    eTag = prepared.eTag,
                    lastModified = prepared.lastModified,
                    supportsRange = prepared.supportsRange,
                    checksumType = detail.checksumType,
                    checksumValue = detail.checksumValue,
                    sourcePolicy = detail.sourcePolicy,
                ),
                control = control,
            ) { event ->
                when (event) {
                    DownloadEvent.Waiting -> {
                        if (control.isStopRequested()) return@download
                        // 进入等待态时刷新更新时间，避免列表里继续展示旧状态。
                        saveRecord(prepared.copy(status = DownloadStatus.WAITING, updatedAt = System.currentTimeMillis()))
                        stateCenter.updateDownload(appId, DownloadStatus.WAITING, progress = prepared.progress)
                    }

                    is DownloadEvent.MetaReady -> {
                        if (control.isStopRequested()) return@download
                        // 元数据就绪后记录总大小、ETag 和 Range 能力，供恢复和校验使用。
                        val updated = repository.getDownloadTask(appId)?.copy(
                            totalBytes = event.meta.contentLength.takeIf { it > 0L } ?: prepared.totalBytes,
                            eTag = event.meta.eTag,
                            lastModified = event.meta.lastModified,
                            supportsRange = event.meta.supportsRange,
                            updatedAt = System.currentTimeMillis(),
                        ) ?: prepared
                        saveRecord(updated)
                    }

                    is DownloadEvent.Running -> {
                        if (control.isStopRequested()) return@download
                        // 下载过程中把字节进度映射成页面进度，并同步速度等运行态信息。
                        val progress = calculateProgress(event.downloadedBytes, event.totalBytes)
                        val updated = repository.getDownloadTask(appId)?.copy(
                            status = DownloadStatus.RUNNING,
                            progress = progress,
                            downloadedBytes = event.downloadedBytes,
                            totalBytes = event.totalBytes,
                            speedBytesPerSec = event.speedBytesPerSec,
                            updatedAt = System.currentTimeMillis(),
                            failureCode = null,
                            failureMessage = null,
                        ) ?: prepared.copy(
                            status = DownloadStatus.RUNNING,
                            progress = progress,
                            downloadedBytes = event.downloadedBytes,
                            totalBytes = event.totalBytes,
                            speedBytesPerSec = event.speedBytesPerSec,
                            updatedAt = System.currentTimeMillis(),
                        )
                        saveRecord(updated)
                        stateCenter.updateDownload(
                            appId = appId,
                            status = DownloadStatus.RUNNING,
                            progress = progress,
                            localApkPath = null,
                            errorMessage = null,
                            errorCode = null,
                        )
                    }

                    is DownloadEvent.Stopped -> {
                        // 底层已经按请求停下时，再统一回写暂停或取消状态，避免只停留在 UI 假状态。
                        when (event.reason) {
                            DownloadStopReason.PAUSED -> markPaused(
                                appId = appId,
                                record = repository.getDownloadTask(appId) ?: prepared,
                                downloadedBytes = event.downloadedBytes,
                                totalBytes = event.totalBytes,
                            )

                            DownloadStopReason.CANCELED -> markCanceled(
                                appId = appId,
                                record = repository.getDownloadTask(appId) ?: prepared,
                            )
                        }
                    }

                    is DownloadEvent.Completed -> {
                        if (control.isStopRequested()) return@download
                        // 下载完成后同时收口 APK 路径、清空分片记录，并把任务切换到完成态。
                        repository.saveDownloadedApk(appId, event.file.absolutePath)
                        repository.saveDownloadSegments(appId, emptyList())
                        val updated = repository.getDownloadTask(appId)?.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 100,
                            downloadedBytes = event.totalBytes,
                            totalBytes = event.totalBytes,
                            speedBytesPerSec = 0L,
                            targetFilePath = event.file.absolutePath,
                            updatedAt = System.currentTimeMillis(),
                            failureCode = null,
                            failureMessage = null,
                        ) ?: prepared.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 100,
                            downloadedBytes = event.totalBytes,
                            totalBytes = event.totalBytes,
                            speedBytesPerSec = 0L,
                            targetFilePath = event.file.absolutePath,
                            updatedAt = System.currentTimeMillis(),
                        )
                        saveRecord(updated)
                        stateCenter.updateDownload(
                            appId = appId,
                            status = DownloadStatus.COMPLETED,
                            progress = 100,
                            localApkPath = event.file.absolutePath,
                            errorMessage = null,
                            errorCode = null,
                        )
                        tracker.track("download_success_$appId")
                    }

                    is DownloadEvent.Failed -> {
                        if (control.isStopRequested()) return@download
                        // 底层失败统一在这里归一化，避免页面直接感知下载器内部细节。
                        markFailed(
                            appId = appId,
                            record = repository.getDownloadTask(appId) ?: prepared,
                            errorCode = event.code.name,
                            errorMessage = event.message,
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            if (control.isStopRequested()) return
            // 兜底处理下载器未归一化的异常，避免任务停留在中间状态。
            logger.d("DownloadManager", "download failed: $appId, ${t.message}")
            markFailed(
                appId = appId,
                record = repository.getDownloadTask(appId) ?: prepared,
                errorCode = DownloadFailureCode.UNKNOWN.name,
                errorMessage = t.message ?: DownloadFailureCode.UNKNOWN.displayText,
            )
        }
    }

    /** 冷启动时恢复持久化任务，并根据用户偏好决定是否自动恢复或自动重试。 */
    private suspend fun restorePersistedTasks() {
        val preferences = repository.getDownloadPreferences()
        val normalizedTasks = repository.getAllDownloadTasks().map { task ->
            // 先把上次异常中断的任务规范化，确保页面和持久化状态一致。
            val normalized = normalizeRecoveredTask(task)
            if (normalized != task) {
                saveRecord(normalized)
            }
            syncState(normalized)
            normalized
        }
        normalizedTasks.forEach { task ->
            when {
                shouldAutoResume(task, preferences) -> {
                    // 自动恢复用于处理上次中断但仍可续传的任务。
                    logger.d("DownloadManager", "auto resume download: ${task.appId}")
                    startDownload(task.appId)
                }
                shouldAutoRetry(task, preferences) -> {
                    // 自动重试用于处理可重试失败态，避免用户每次冷启动都手动操作。
                    logger.d("DownloadManager", "auto retry download: ${task.appId}")
                    startDownload(task.appId)
                }
            }
        }
    }

    /** 将失败态同时回写到持久化记录和状态中心。 */
    private suspend fun markFailed(
        appId: String,
        record: DownloadTaskRecord?,
        errorCode: String,
        errorMessage: String,
    ) {
        val now = System.currentTimeMillis()
        val failedRecord = record?.copy(
            status = DownloadStatus.FAILED,
            speedBytesPerSec = 0L,
            retryCount = record.retryCount + 1,
            updatedAt = now,
            failureCode = errorCode,
            failureMessage = errorMessage,
        )
        if (failedRecord != null) {
            saveRecord(failedRecord)
        }
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.FAILED,
            progress = record?.progress ?: 0,
            localApkPath = null,
            errorMessage = errorMessage,
            errorCode = errorCode,
        )
        tracker.track("download_fail_${errorCode.lowercase()}_$appId")
    }

    /** 将暂停态同时回写到持久化记录和状态中心。 */
    private suspend fun markPaused(
        appId: String,
        record: DownloadTaskRecord,
        downloadedBytes: Long,
        totalBytes: Long,
    ) {
        val normalizedTotalBytes = totalBytes.takeIf { it > 0L } ?: record.totalBytes
        val progress = calculateProgress(downloadedBytes, normalizedTotalBytes)
        val pausedRecord = record.copy(
            status = DownloadStatus.PAUSED,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = normalizedTotalBytes,
            speedBytesPerSec = 0L,
            failureCode = null,
            failureMessage = null,
            updatedAt = System.currentTimeMillis(),
        )
        saveRecord(pausedRecord)
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.PAUSED,
            progress = progress,
            localApkPath = null,
            errorMessage = null,
            errorCode = null,
        )
    }

    /** 将取消态同时回写到持久化记录和状态中心。 */
    private suspend fun markCanceled(
        appId: String,
        record: DownloadTaskRecord,
    ) {
        repository.clearDownloadedApk(appId)
        repository.saveDownloadSegments(appId, emptyList())
        saveRecord(
            record.copy(
                status = DownloadStatus.CANCELED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                speedBytesPerSec = 0L,
                failureCode = DownloadFailureCode.USER_CANCELED.name,
                failureMessage = DownloadFailureCode.USER_CANCELED.displayText,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.CANCELED,
            progress = 0,
            localApkPath = null,
            errorMessage = DownloadFailureCode.USER_CANCELED.displayText,
            errorCode = DownloadFailureCode.USER_CANCELED.name,
        )
    }

    /** 保存下载任务记录。 */
    private suspend fun saveRecord(record: DownloadTaskRecord) {
        repository.saveDownloadTask(record)
    }

    /** 注册新的活动下载任务，若已存在则拒绝重复启动。 */
    private suspend fun registerExecution(appId: String, execution: ActiveDownloadExecution): Boolean {
        return executionMutex.withLock {
            val current = activeExecutions[appId]
            if (current != null && !current.job.isCompleted) {
                false
            } else {
                activeExecutions[appId] = execution
                true
            }
        }
    }

    /** 读取当前仍然活跃的下载任务句柄。 */
    private suspend fun getActiveExecution(appId: String): ActiveDownloadExecution? {
        return executionMutex.withLock {
            val execution = activeExecutions[appId]
            if (execution != null && !execution.job.isCompleted) {
                execution
            } else {
                activeExecutions.remove(appId)
                null
            }
        }
    }

    /** 在任务结束时清理活动下载任务注册表。 */
    private suspend fun unregisterExecution(appId: String, control: DownloadExecutionControl) {
        executionMutex.withLock {
            val execution = activeExecutions[appId]
            if (execution?.control === control) {
                activeExecutions.remove(appId)
            }
        }
    }

    /** 根据持久化任务记录恢复页面运行态。 */
    private suspend fun syncState(record: DownloadTaskRecord) {
        val apkPath = resolveDownloadedApkPath(record)
        when (record.status) {
            DownloadStatus.COMPLETED -> stateCenter.updateDownload(
                appId = record.appId,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                localApkPath = apkPath,
                errorMessage = null,
                errorCode = null,
            )

            DownloadStatus.PAUSED, DownloadStatus.RUNNING, DownloadStatus.WAITING -> stateCenter.updateDownload(
                appId = record.appId,
                status = record.status,
                progress = record.progress,
                localApkPath = null,
                errorMessage = record.failureMessage,
                errorCode = record.failureCode,
            )

            DownloadStatus.FAILED, DownloadStatus.CANCELED, DownloadStatus.IDLE -> stateCenter.updateDownload(
                appId = record.appId,
                status = record.status,
                progress = record.progress,
                localApkPath = null,
                errorMessage = record.failureMessage,
                errorCode = record.failureCode,
            )
        }
    }

    /** 规范化冷启动恢复时读到的旧任务记录。 */
    private suspend fun normalizeRecoveredTask(record: DownloadTaskRecord): DownloadTaskRecord {
        val now = System.currentTimeMillis()
        val apkPath = resolveDownloadedApkPath(record)
        val targetFile = File(record.targetFilePath)
        val downloadedBytes = when {
            targetFile.exists() -> targetFile.length()
            record.downloadedBytes > 0L -> record.downloadedBytes
            else -> 0L
        }
        val totalBytes = record.totalBytes.takeIf { it > 0L } ?: downloadedBytes
        val progress = when {
            totalBytes > 0L -> calculateProgress(downloadedBytes, totalBytes)
            record.progress > 0 -> record.progress
            else -> 0
        }

        return when {
            // 已完成任务如果找不到 APK，说明持久化记录已失真，需要直接转成失败态。
            record.status == DownloadStatus.COMPLETED && apkPath == null -> record.copy(
                status = DownloadStatus.FAILED,
                progress = 0,
                downloadedBytes = 0L,
                speedBytesPerSec = 0L,
                failureCode = DownloadFailureCode.FILE_MISSING.name,
                failureMessage = DownloadFailureCode.FILE_MISSING.displayText,
                updatedAt = now,
            )

            // 上次停在运行/等待中间态的任务，冷启动后统一转成可恢复的暂停态。
            record.status == DownloadStatus.RUNNING || record.status == DownloadStatus.WAITING -> record.copy(
                status = DownloadStatus.PAUSED,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                speedBytesPerSec = 0L,
                failureCode = null,
                failureMessage = if (downloadedBytes > 0L) BusinessText.DOWNLOAD_INTERRUPTED_RESUMABLE else null,
                updatedAt = now,
            )

            // 文件大小或进度与历史记录不一致时，用当前磁盘事实修正记录。
            record.downloadedBytes != downloadedBytes || record.totalBytes != totalBytes || record.progress != progress -> record.copy(
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                updatedAt = now,
            )

            else -> record
        }
    }

    /** 解析当前任务对应的 APK 路径，优先使用仍然存在的目标文件。 */
    private suspend fun resolveDownloadedApkPath(record: DownloadTaskRecord): String? {
        val recordPath = record.targetFilePath.takeIf { it.isNotBlank() && File(it).exists() }
        val artifactPath = repository.getDownloadedApk(record.appId)?.takeIf { it.isNotBlank() && File(it).exists() }
        return recordPath ?: artifactPath
    }

    /** 判断当前任务是否符合自动恢复条件。 */
    private fun shouldAutoResume(record: DownloadTaskRecord, preferences: DownloadPreferences): Boolean {
        return preferences.autoResumeOnLaunch && (record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.WAITING) && record.downloadUrl != null
    }

    /** 判断当前任务是否符合自动重试条件。 */
    private fun shouldAutoRetry(record: DownloadTaskRecord, preferences: DownloadPreferences): Boolean {
        return preferences.autoRetryEnabled && record.status == DownloadStatus.FAILED && record.retryCount < preferences.maxAutoRetryCount && record.downloadUrl != null
    }

    /** 将字节进度转换为百分比进度。 */
    private fun calculateProgress(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        return ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    }

    /** 创建新的下载任务记录。 */
    private fun newRecord(appId: String, detail: AppDetail, targetFilePath: String, now: Long): DownloadTaskRecord {
        return DownloadTaskRecord(
            taskId = "download-$appId",
            appId = appId,
            status = DownloadStatus.IDLE,
            progress = 0,
            targetFilePath = targetFilePath,
            downloadedBytes = 0L,
            totalBytes = 0L,
            failureCode = null,
            failureMessage = null,
            retryCount = 0,
            downloadUrl = detail.apkUrl,
            tempDirPath = null,
            eTag = null,
            lastModified = null,
            supportsRange = false,
            checksumType = detail.checksumType,
            checksumValue = detail.checksumValue,
            segmentCount = 1,
            createdAt = now,
            updatedAt = now,
        )
    }
}
