package com.nio.appstore.core.downloader

import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class SimulatedFileDownloader(
    private val chunkBytes: Long = 64L * 1024L,
    private val tickMs: Long = 220L,
    private val failPlan: (appId: String, attempt: Int) -> DownloadFailureCode? = { _, _ -> null },
) : FileDownloader {

    override suspend fun download(
        request: DownloadRequest,
        onEvent: suspend (DownloadEvent) -> Unit,
    ) {
        onEvent(DownloadEvent.Waiting)
        delay(150L)
        val totalBytes = request.totalBytes.takeIf { it > 0L } ?: 512L * 1024L
        onEvent(DownloadEvent.MetaReady(DownloadRemoteMeta(contentLength = totalBytes, supportsRange = true, mimeType = "application/vnd.android.package-archive")))

        val targetFile = request.targetFile
        targetFile.parentFile?.mkdirs()
        if (!targetFile.exists()) targetFile.createNewFile()

        val plannedFailure = failPlan(request.appId, request.attempt)
        val failTriggerBytes = if (plannedFailure != null) {
            (request.totalBytes.coerceAtLeast(512L * 1024L) / 2L).coerceAtLeast(chunkBytes)
        } else {
            Long.MAX_VALUE
        }

        var downloaded = targetFile.length().coerceAtLeast(request.downloadedBytes).coerceAtMost(totalBytes)
        while (downloaded < totalBytes) {
            delay(tickMs)
            val delta = chunkBytes.coerceAtMost(totalBytes - downloaded)
            appendChunk(targetFile, downloaded, delta)
            downloaded = (downloaded + delta).coerceAtMost(request.totalBytes)

            if (plannedFailure != null && downloaded >= failTriggerBytes) {
                onEvent(DownloadEvent.Failed(plannedFailure, plannedFailure.displayText))
                return
            }

            val speed = ((delta * 1000L) / tickMs).coerceAtLeast(1L)
            onEvent(DownloadEvent.Running(downloaded, totalBytes, speed))
        }
        onEvent(DownloadEvent.Completed(targetFile, totalBytes))
    }

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

class DownloadIOException(cause: Throwable) : IOException(cause)
