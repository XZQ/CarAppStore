package com.nio.appstore.domain.download

import com.nio.appstore.core.downloader.DownloadEvent
import com.nio.appstore.core.downloader.DownloadFailureCode
import com.nio.appstore.core.downloader.DownloadRequest
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class DefaultDownloadManager(
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
    private val policyCenter: PolicyCenter,
    private val fileDownloader: FileDownloader,
    private val logger: AppLogger,
    private val tracker: EventTracker,
) : DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            restorePersistedTasks()
        }
    }

    override suspend fun startDownload(appId: String) {
        val policy = policyCenter.canDownload(appId)
        if (!policy.allow) {
            markFailed(
                appId = appId,
                record = repository.getDownloadTask(appId),
                errorCode = DownloadFailureCode.UNKNOWN.name,
                errorMessage = "下载受限：${policy.reason}",
            )
            return
        }

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
            ) { event ->
                when (event) {
                    DownloadEvent.Waiting -> {
                        saveRecord(
                            prepared.copy(
                                status = DownloadStatus.WAITING,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        stateCenter.updateDownload(appId, DownloadStatus.WAITING, progress = prepared.progress)
                    }

                    is DownloadEvent.MetaReady -> {
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

                    is DownloadEvent.Completed -> {
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
            logger.d("DownloadManager", "download failed: $appId, ${t.message}")
            markFailed(
                appId = appId,
                record = repository.getDownloadTask(appId) ?: prepared,
                errorCode = DownloadFailureCode.UNKNOWN.name,
                errorMessage = t.message ?: DownloadFailureCode.UNKNOWN.displayText,
            )
        }
    }

    override suspend fun pauseDownload(appId: String) {
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

    override suspend fun resumeDownload(appId: String) {
        val record = repository.getDownloadTask(appId)
        if (record == null || record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.FAILED || record.status == DownloadStatus.CANCELED) {
            startDownload(appId)
        }
    }

    override suspend fun cancelDownload(appId: String) {
        val record = repository.getDownloadTask(appId) ?: return
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

    override suspend fun removeTask(appId: String, clearFile: Boolean) {
        val snapshot = stateCenter.snapshot(appId)
        if (clearFile) {
            repository.clearDownloadedApk(appId)
        }
        repository.saveDownloadSegments(appId, emptyList())
        repository.removeDownloadTask(appId)
        stateCenter.updateDownload(
            appId = appId,
            status = DownloadStatus.IDLE,
            progress = 0,
            localApkPath = null,
            errorMessage = null,
            errorCode = null,
        )
        if (clearFile && snapshot.installStatus != InstallStatus.INSTALLED) {
            stateCenter.updateInstall(
                appId = appId,
                status = InstallStatus.NOT_INSTALLED,
                versionName = null,
                errorMessage = null,
                errorCode = null,
            )
        }
    }

    override suspend fun clearCompletedTasks(): Int {
        val completedTasks = repository.getAllDownloadTasks().filter {
            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELED
        }
        completedTasks.forEach { removeTask(it.appId, clearFile = true) }
        return completedTasks.size
    }

    override suspend fun retryFailedTasks(): Int {
        val failedTasks = repository.getAllDownloadTasks().filter {
            it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED
        }
        failedTasks.forEach { startDownload(it.appId) }
        return failedTasks.size
    }

    override suspend fun getPreferences(): DownloadPreferences = repository.getDownloadPreferences()

    override suspend fun updatePreferences(preferences: DownloadPreferences) {
        repository.saveDownloadPreferences(preferences)
    }

    private suspend fun restorePersistedTasks() {
        val preferences = repository.getDownloadPreferences()
        val normalizedTasks = repository.getAllDownloadTasks().map { task ->
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
                    logger.d("DownloadManager", "auto resume download: ${task.appId}")
                    startDownload(task.appId)
                }
                shouldAutoRetry(task, preferences) -> {
                    logger.d("DownloadManager", "auto retry download: ${task.appId}")
                    startDownload(task.appId)
                }
            }
        }
    }

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

    private suspend fun saveRecord(record: DownloadTaskRecord) {
        repository.saveDownloadTask(record)
    }

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
            DownloadStatus.PAUSED,
            DownloadStatus.RUNNING,
            DownloadStatus.WAITING -> stateCenter.updateDownload(
                appId = record.appId,
                status = record.status,
                progress = record.progress,
                localApkPath = null,
                errorMessage = record.failureMessage,
                errorCode = record.failureCode,
            )
            DownloadStatus.FAILED,
            DownloadStatus.CANCELED,
            DownloadStatus.IDLE -> stateCenter.updateDownload(
                appId = record.appId,
                status = record.status,
                progress = record.progress,
                localApkPath = null,
                errorMessage = record.failureMessage,
                errorCode = record.failureCode,
            )
        }
    }

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
            record.status == DownloadStatus.COMPLETED && apkPath == null -> record.copy(
                status = DownloadStatus.FAILED,
                progress = 0,
                downloadedBytes = 0L,
                speedBytesPerSec = 0L,
                failureCode = DownloadFailureCode.FILE_MISSING.name,
                failureMessage = DownloadFailureCode.FILE_MISSING.displayText,
                updatedAt = now,
            )
            record.status == DownloadStatus.RUNNING || record.status == DownloadStatus.WAITING -> record.copy(
                status = DownloadStatus.PAUSED,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                speedBytesPerSec = 0L,
                failureCode = null,
                failureMessage = if (downloadedBytes > 0L) "上次下载中断，可继续下载" else null,
                updatedAt = now,
            )
            record.downloadedBytes != downloadedBytes || record.totalBytes != totalBytes || record.progress != progress -> record.copy(
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                updatedAt = now,
            )
            else -> record
        }
    }

    private suspend fun resolveDownloadedApkPath(record: DownloadTaskRecord): String? {
        val recordPath = record.targetFilePath.takeIf { it.isNotBlank() && File(it).exists() }
        return recordPath ?: repository.getDownloadedApk(record.appId)
    }

    private fun shouldAutoResume(record: DownloadTaskRecord, preferences: DownloadPreferences): Boolean {
        return preferences.autoResumeOnLaunch &&
            (record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.WAITING) &&
            record.downloadUrl != null
    }

    private fun shouldAutoRetry(record: DownloadTaskRecord, preferences: DownloadPreferences): Boolean {
        return preferences.autoRetryEnabled &&
            record.status == DownloadStatus.FAILED &&
            record.retryCount < preferences.maxAutoRetryCount &&
            record.downloadUrl != null
    }

    private fun calculateProgress(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        return ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    }

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
