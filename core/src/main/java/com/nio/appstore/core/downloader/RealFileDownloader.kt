package com.nio.appstore.core.downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class RealFileDownloader(
    private val store: DownloadStore,
    private val sourceResolver: DownloadSourceResolver,
    private val segmentPlanner: SegmentPlanner = SegmentPlanner(),
    private val fallbackDownloader: FileDownloader? = null,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val chunkBytes: Int = 32 * 1024,
    private val maxParallelSegments: Int = 2,
    private val maxSegmentRetryCount: Int = 2,
) : FileDownloader {

    override suspend fun download(
        request: DownloadRequest,
        onEvent: suspend (DownloadEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val sourceDecision = sourceResolver.resolve(request.sourcePolicy, request.url)
        if (sourceDecision.policy != DownloadSourcePolicy.DIRECT_HTTP) {
            fallbackDownloader?.download(request, onEvent)
                ?: onEvent(DownloadEvent.Failed(DownloadFailureCode.UNKNOWN, sourceDecision.reason))
            return@withContext
        }

        onEvent(DownloadEvent.Waiting)
        val meta = try {
            probeRemoteMeta(request.url)
        } catch (e: SocketTimeoutException) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.NETWORK_TIMEOUT, e.message ?: DownloadFailureCode.NETWORK_TIMEOUT.displayText))
            return@withContext
        } catch (e: IOException) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.NETWORK_INTERRUPTED, e.message ?: DownloadFailureCode.NETWORK_INTERRUPTED.displayText))
            return@withContext
        }
        store.saveMeta(request.taskId, meta)
        onEvent(DownloadEvent.MetaReady(meta))

        if (request.eTag != null && meta.eTag != null && request.eTag != meta.eTag) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.REMOTE_FILE_CHANGED, DownloaderText.REMOTE_FILE_CHANGED_REDOWNLOAD, false))
            return@withContext
        }
        if (request.lastModified != null && meta.lastModified != null && request.lastModified != meta.lastModified) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.REMOTE_FILE_CHANGED, DownloaderText.REMOTE_FILE_CHANGED_REDOWNLOAD, false))
            return@withContext
        }

        val totalBytes = when {
            request.totalBytes > 0L -> request.totalBytes
            meta.contentLength > 0L -> meta.contentLength
            else -> 0L
        }

        request.targetFile.parentFile?.mkdirs()
        val taskTempDir = store.getTaskTempDir(request.taskId)
        if (!request.targetFile.exists()) request.targetFile.createNewFile()

        val existingSegments = store.readSegments(request.taskId)
        val plannedSegments = segmentPlanner.plan(
            taskId = request.taskId,
            tempDir = taskTempDir,
            totalBytes = totalBytes,
            requestedSegmentCount = 2,
            existingSegments = existingSegments,
        ).sortedBy { it.index }
        store.saveSegments(request.taskId, plannedSegments)

        val eventMutex = Mutex()
        val segmentResults = mutableListOf<SegmentResult>()
        var failedResult: SegmentResult? = null

        coroutineScope {
            for (batch in plannedSegments.chunked(maxParallelSegments.coerceAtLeast(1))) {
                val results = batch.map { segment ->
                    async(Dispatchers.IO) {
                        downloadSegmentWithRetry(
                            request = request,
                            meta = meta,
                            segment = segment,
                            onProgress = { speed ->
                                eventMutex.withLock {
                                    onEvent(
                                        DownloadEvent.Running(
                                            downloadedBytes = calculateAggregateDownloaded(request.taskId),
                                            totalBytes = totalBytes,
                                            speedBytesPerSec = speed,
                                        )
                                    )
                                }
                            }
                        )
                    }
                }.awaitAll()

                val firstFailure = results.firstOrNull { !it.success }
                if (firstFailure != null) {
                    failedResult = firstFailure
                    break
                }
                segmentResults += results
            }
        }
        failedResult?.let { failure ->
            onEvent(
                DownloadEvent.Failed(
                    code = failure.code ?: DownloadFailureCode.UNKNOWN,
                    message = failure.message ?: (failure.code?.displayText ?: DownloaderText.UNKNOWN_DOWNLOAD_FAILURE),
                    retryable = failure.code?.retryable ?: true,
                )
            )
            return@withContext
        }

        val finalFile = request.targetFile
        if (finalFile.exists()) finalFile.delete()
        val mergeOk = mergeSegments(plannedSegments, finalFile)
        if (!mergeOk) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.MERGE_FAILED, DownloadFailureCode.MERGE_FAILED.displayText, false))
            return@withContext
        }

        val finalSize = finalFile.length()
        val verification = verifyDownloadedFile(
            file = finalFile,
            expectedBytes = totalBytes,
            checksumType = request.checksumType,
            checksumValue = request.checksumValue,
        )
        if (!verification.ok) {
            onEvent(DownloadEvent.Failed(verification.code, verification.message, verification.code.retryable))
            return@withContext
        }

        plannedSegments.forEach { seg ->
            saveSegmentRecord(
                request = request,
                segment = seg,
                downloadedBytes = File(seg.tmpFilePath).takeIf { it.exists() }?.length() ?: seg.downloadedBytes,
                status = DownloaderText.STATUS_COMPLETED,
                retryCount = segmentResults.firstOrNull { it.segmentId == seg.segmentId }?.attempts ?: request.attempt,
            )
        }
        onEvent(DownloadEvent.Completed(finalFile, if (totalBytes > 0L) totalBytes else finalSize))
    }

    private suspend fun downloadSegmentWithRetry(
        request: DownloadRequest,
        meta: DownloadRemoteMeta,
        segment: DownloadSegmentRecord,
        onProgress: suspend (speedBytesPerSec: Long) -> Unit,
    ): SegmentResult {
        var attempt = 0
        while (attempt <= maxSegmentRetryCount) {
            val result = downloadSingleSegment(
                request = request,
                meta = meta,
                segment = segment,
                attempt = attempt,
                onProgress = onProgress,
            )
            if (result.success) return result
            if (!(result.code?.retryable ?: false) || attempt >= maxSegmentRetryCount) {
                return result
            }
            attempt++
        }
        return SegmentResult(
            segmentId = segment.segmentId,
            success = false,
            code = DownloadFailureCode.UNKNOWN,
            message = DownloaderText.SEGMENT_DOWNLOAD_FAILED,
            attempts = maxSegmentRetryCount,
        )
    }

    private suspend fun downloadSingleSegment(
        request: DownloadRequest,
        meta: DownloadRemoteMeta,
        segment: DownloadSegmentRecord,
        attempt: Int,
        onProgress: suspend (speedBytesPerSec: Long) -> Unit,
    ): SegmentResult {
        val partFile = File(segment.tmpFilePath)
        if (!partFile.exists()) partFile.createNewFile()

        val existingBytes = maxOf(segment.downloadedBytes, partFile.length())
        val resumeOffset = if (existingBytes > 0L) segment.startByte + existingBytes else segment.startByte

        if (existingBytes > 0L && !meta.supportsRange) {
            return SegmentResult(
                segmentId = segment.segmentId,
                success = false,
                code = DownloadFailureCode.RANGE_NOT_SUPPORTED,
                message = DownloadFailureCode.RANGE_NOT_SUPPORTED.displayText,
                attempts = attempt,
            )
        }

        saveSegmentRecord(
            request = request,
            segment = segment,
            downloadedBytes = existingBytes,
            status = if (existingBytes > 0L) DownloaderText.STATUS_RESUMING else DownloaderText.STATUS_WAITING,
            retryCount = attempt,
        )

        val connection = openConnection(
            request.url,
            rangeStart = if (resumeOffset > 0L) resumeOffset else null,
            rangeEnd = if (segment.endByte >= segment.startByte) segment.endByte else null,
        )
        try {
            val code = connection.responseCode
            when {
                existingBytes > 0L && code != HttpURLConnection.HTTP_PARTIAL -> {
                    return SegmentResult(segment.segmentId, false, DownloadFailureCode.RANGE_NOT_SUPPORTED, DownloaderText.RANGE_RESPONSE_INVALID, attempt)
                }
                code in 200..206 -> Unit
                code in 400..499 -> {
                    return SegmentResult(segment.segmentId, false, DownloadFailureCode.HTTP_4XX, "HTTP $code", attempt)
                }
                code >= 500 -> {
                    return SegmentResult(segment.segmentId, false, DownloadFailureCode.HTTP_5XX, "HTTP $code", attempt)
                }
            }

            RandomAccessFile(partFile, "rw").use { out ->
                if (existingBytes > 0L) out.seek(existingBytes) else out.setLength(0L)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(chunkBytes)
                    var segmentDownloaded = existingBytes
                    val startedAt = System.currentTimeMillis()
                    var read = input.read(buffer)
                    while (read >= 0) {
                        out.write(buffer, 0, read)
                        segmentDownloaded += read
                        saveSegmentRecord(
                            request = request,
                            segment = segment,
                            downloadedBytes = segmentDownloaded,
                            status = DownloaderText.STATUS_RUNNING,
                            retryCount = attempt,
                        )
                        val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                        val speed = ((segmentDownloaded - existingBytes).coerceAtLeast(1L) * 1000L / elapsedMs).coerceAtLeast(1L)
                        onProgress(speed)
                        read = input.read(buffer)
                    }
                }
            }

            val finalDownloaded = maxOf(existingBytes, partFile.length())
            val expectedLength = if (segment.endByte >= segment.startByte) (segment.endByte - segment.startByte + 1L) else finalDownloaded
            if (expectedLength > 0L && finalDownloaded < expectedLength) {
                saveSegmentRecord(request, segment, finalDownloaded, DownloaderText.STATUS_FAILED_INCOMPLETE, retryCount = attempt)
                return SegmentResult(segment.segmentId, false, DownloadFailureCode.FILE_INCOMPLETE, DownloaderText.SEGMENT_FILE_INCOMPLETE, attempt)
            }

            saveSegmentRecord(request, segment, finalDownloaded, DownloaderText.STATUS_COMPLETED, retryCount = attempt)
            return SegmentResult(segment.segmentId, true, attempts = attempt)
        } catch (e: SocketTimeoutException) {
            saveSegmentRecord(request, segment, existingBytes, DownloaderText.STATUS_FAILED_TIMEOUT, retryCount = attempt)
            return SegmentResult(segment.segmentId, false, DownloadFailureCode.NETWORK_TIMEOUT, e.message ?: DownloadFailureCode.NETWORK_TIMEOUT.displayText, attempt)
        } catch (e: IOException) {
            saveSegmentRecord(request, segment, existingBytes, DownloaderText.STATUS_FAILED_IO, retryCount = attempt)
            return SegmentResult(segment.segmentId, false, DownloadFailureCode.NETWORK_INTERRUPTED, e.message ?: DownloadFailureCode.NETWORK_INTERRUPTED.displayText, attempt)
        } finally {
            connection.disconnect()
        }
    }

    private fun calculateAggregateDownloaded(taskId: String): Long {
        return store.readSegments(taskId).sumOf { seg ->
            maxOf(seg.downloadedBytes, File(seg.tmpFilePath).takeIf { it.exists() }?.length() ?: 0L)
        }
    }

    private fun saveSegmentRecord(
        request: DownloadRequest,
        segment: DownloadSegmentRecord,
        downloadedBytes: Long,
        status: String,
        retryCount: Int,
    ) {
        val now = System.currentTimeMillis()
        val current = store.readSegments(request.taskId).associateBy { it.segmentId }.toMutableMap()
        current[segment.segmentId] = segment.copy(
            downloadedBytes = downloadedBytes,
            status = status,
            retryCount = retryCount,
            updatedAt = now,
        )
        store.saveSegments(request.taskId, current.values.sortedBy { it.index })
    }

    private fun mergeSegments(
        segments: List<DownloadSegmentRecord>,
        finalFile: File,
    ): Boolean {
        return runCatching {
            RandomAccessFile(finalFile, "rw").use { out ->
                out.setLength(0L)
                segments.sortedBy { it.index }.forEach { seg ->
                    val partFile = File(seg.tmpFilePath)
                    if (!partFile.exists()) throw IOException("missing segment ${seg.index}")
                    partFile.inputStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            if (read > 0) {
                                out.write(buffer, 0, read)
                            }
                            read = input.read(buffer)
                        }
                    }
                }
            }
        }.isSuccess
    }

    private data class VerificationResult(
        val ok: Boolean,
        val code: DownloadFailureCode = DownloadFailureCode.UNKNOWN,
        val message: String = "",
    )

    private data class SegmentResult(
        val segmentId: String,
        val success: Boolean,
        val code: DownloadFailureCode? = null,
        val message: String? = null,
        val attempts: Int = 0,
    )

    private fun verifyDownloadedFile(
        file: File,
        expectedBytes: Long,
        checksumType: String?,
        checksumValue: String?,
    ): VerificationResult {
        if (!file.exists()) {
            return VerificationResult(false, DownloadFailureCode.FILE_MISSING, DownloaderText.FILE_NOT_EXISTS)
        }
        val actual = file.length()
        if (expectedBytes > 0L && actual != expectedBytes) {
            return VerificationResult(false, DownloadFailureCode.FILE_INCOMPLETE, DownloaderText.fileLengthMismatch(expectedBytes, actual))
        }
        if (!checksumType.isNullOrBlank() && !checksumValue.isNullOrBlank()) {
            val actualHash = calculateHash(file, checksumType)
            if (!actualHash.equals(checksumValue, ignoreCase = true)) {
                return VerificationResult(false, DownloadFailureCode.CHECKSUM_MISMATCH, DownloaderText.checksumMismatch(checksumType))
            }
        }
        return VerificationResult(true)
    }

    private fun calculateHash(file: File, checksumType: String): String {
        val algo = when (checksumType.uppercase()) {
            "SHA-256", "SHA256" -> "SHA-256"
            "MD5" -> "MD5"
            else -> "SHA-256"
        }
        val digest = MessageDigest.getInstance(algo)
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun probeRemoteMeta(url: String): DownloadRemoteMeta {
        val headConnection = openConnection(url, head = true)
        return try {
            headConnection.connect()
            val contentLength = headConnection.getHeaderFieldLong("Content-Length", -1L)
            val acceptsRange = headConnection.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            val eTag = headConnection.getHeaderField("ETag")
            val lastModified = headConnection.getHeaderField("Last-Modified")
            val mimeType = headConnection.contentType
            DownloadRemoteMeta(
                contentLength = contentLength,
                eTag = eTag,
                lastModified = lastModified,
                supportsRange = acceptsRange,
                mimeType = mimeType,
            )
        } finally {
            headConnection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        head: Boolean = false,
        rangeStart: Long? = null,
        rangeEnd: Long? = null,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = if (head) "HEAD" else "GET"
        connection.setRequestProperty("Accept", "*/*")
        if (rangeStart != null) {
            val value = if (rangeEnd != null && rangeEnd >= rangeStart) {
                "bytes=$rangeStart-$rangeEnd"
            } else {
                "bytes=$rangeStart-"
            }
            connection.setRequestProperty("Range", value)
        }
        connection.instanceFollowRedirects = true
        connection.doInput = true
        return connection
    }
}
