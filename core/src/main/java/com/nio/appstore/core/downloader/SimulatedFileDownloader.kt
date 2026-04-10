package com.nio.appstore.core.downloader

import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class SimulatedFileDownloader(
    /** 每个 tick 写入的模拟字节数。 */
    private val chunkBytes: Long = 64L * 1024L,
    /** 每轮进度推进之间的等待时间。 */
    private val tickMs: Long = 220L,
    /** 用于注入失败场景的计划函数。 */
    private val failPlan: (appId: String, attempt: Int) -> DownloadFailureCode? = { _, _ -> null },
) : FileDownloader {

    /** 通过本地文件写入和延时来模拟真实下载过程。 */
    override suspend fun download(
        request: DownloadRequest,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
    ) {
        if (emitStoppedIfRequested(request, control, onEvent, request.downloadedBytes, request.totalBytes)) return
        onEvent(DownloadEvent.Waiting)
        delay(150L)
        val totalBytes = request.totalBytes.takeIf { it > 0L } ?: (512L * 1024L)
        if (emitStoppedIfRequested(request, control, onEvent, request.downloadedBytes, totalBytes)) return
        onEvent(
            DownloadEvent.MetaReady(
                DownloadRemoteMeta(
                    contentLength = totalBytes,
                    supportsRange = true,
                    mimeType = "application/vnd.android.package-archive"
                )
            )
        )

        val targetFile = request.targetFile
        targetFile.parentFile?.mkdirs()
        if (!targetFile.exists()) targetFile.createNewFile()

        // 如果当前尝试被配置为失败，则在下载一半时模拟抛出失败事件。
        val plannedFailure = failPlan(request.appId, request.attempt)
        val failTriggerBytes = if (plannedFailure != null) {
            (request.totalBytes.coerceAtLeast(512L * 1024L) / 2L).coerceAtLeast(chunkBytes)
        } else {
            Long.MAX_VALUE
        }

        var downloaded = targetFile.length().coerceAtLeast(request.downloadedBytes).coerceAtMost(totalBytes)
        while (downloaded < totalBytes) {
            if (emitStoppedIfRequested(request, control, onEvent, downloaded, totalBytes)) return
            delay(tickMs)
            if (emitStoppedIfRequested(request, control, onEvent, downloaded, totalBytes)) return
            val delta = chunkBytes.coerceAtMost(totalBytes - downloaded)
            appendChunk(targetFile, downloaded, delta)
            downloaded = (downloaded + delta).coerceAtMost(totalBytes)

            if (plannedFailure != null && downloaded >= failTriggerBytes) {
                onEvent(DownloadEvent.Failed(plannedFailure, plannedFailure.displayText))
                return
            }

            // 用当前 chunk 的写入速度近似模拟瞬时下载速度。
            val speed = ((delta * 1000L) / tickMs).coerceAtLeast(1L)
            onEvent(DownloadEvent.Running(downloaded, totalBytes, speed))
        }
        if (emitStoppedIfRequested(request, control, onEvent, downloaded, totalBytes)) return
        onEvent(DownloadEvent.Completed(targetFile, totalBytes))
    }

    /** 在模拟下载过程中检测是否已经收到暂停或取消请求。 */
    private suspend fun emitStoppedIfRequested(
        request: DownloadRequest,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
        downloadedBytes: Long,
        totalBytes: Long,
    ): Boolean {
        val reason = control.currentStopReason() ?: return false
        val normalizedTotalBytes = totalBytes.takeIf { it > 0L } ?: request.totalBytes
        onEvent(
            DownloadEvent.Stopped(
                reason = reason,
                downloadedBytes = downloadedBytes,
                totalBytes = normalizedTotalBytes,
            )
        )
        return true
    }

    /** 将一段模拟字节写入到目标文件指定偏移位置。 */
    private fun appendChunk(file: File, offset: Long, byteCount: Long) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset)
                val chunk = ByteArray(byteCount.toInt()) { index -> ((offset + index) % 251).toInt().toByte() }
                raf.write(chunk)
            }
        } catch (io: IOException) {
            throw DownloadIOException(io)
        }
    }
}

/** 用于包装模拟下载器中的 IO 异常。 */
class DownloadIOException(cause: Throwable) : IOException(cause)
