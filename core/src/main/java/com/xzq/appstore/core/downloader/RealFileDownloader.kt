package com.xzq.appstore.core.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class RealFileDownloader(
    /** 下载元数据与分片状态存储。 */
    private val store: DownloadStore,
    /** 下载源决策器，负责决定是否走真实 HTTP 或兜底实现。 */
    private val sourceResolver: DownloadSourceResolver,
    /** 分片规划器，负责根据文件大小生成分片方案。 */
    private val segmentPlanner: SegmentPlanner = SegmentPlanner(),
    /** 当下载源不允许真实 HTTP 时使用的兜底下载器。 */
    private val fallbackDownloader: FileDownloader? = null,
    /** 每次 HEAD/GET 请求都携带的 CDN 鉴权头；签名 URL 模式保持为空。 */
    private val requestHeaders: Map<String, String> = emptyMap(),
    /** 建立连接的超时时间。 */
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    /** 读取响应体的超时时间。 */
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    /** 单次从输入流读取的缓冲区大小。 */
    private val chunkBytes: Int = DEFAULT_CHUNK_BYTES,
    /** 单个下载任务允许同时执行的最大分片数。 */
    private val maxParallelSegments: Int = DEFAULT_MAX_PARALLEL_SEGMENTS,
    /** 单个分片的最大重试次数。 */
    private val maxSegmentRetryCount: Int = DEFAULT_MAX_SEGMENT_RETRY_COUNT,
    /** 运行态刷盘节流间隔，避免每 32KB 一次全量重写。 */
    private val runningFlushIntervalMs: Long = DEFAULT_RUNNING_FLUSH_INTERVAL_MS,
    /** Running 事件发射节流间隔，避免每 32KB 一次事件风暴；0 表示不节流。 */
    private val runningEventIntervalMs: Long = DEFAULT_RUNNING_EVENT_INTERVAL_MS,
    /** 合并前测试钩子，供测试场景注入分片文件扰动。 */
    private val beforeMergeHook: ((segments: List<DownloadSegmentRecord>, finalFile: File) -> Unit)? = null,
) : FileDownloader {
    init {
        require(requestHeaders.keys.all(HTTP_HEADER_NAME_PATTERN::matches)) {
            "download request contains an invalid HTTP header name"
        }
        require(requestHeaders.values.none { it.contains('\r') || it.contains('\n') }) {
            "download request contains an invalid HTTP header value"
        }
    }

    /** 分片内存缓存：taskId -> (segmentId -> record)，避免每 32KB 触发全量 JSON 读写。 */
    private val segmentCache: MutableMap<String, MutableMap<String, DownloadSegmentRecord>> = mutableMapOf()

    /** 上次运行态刷盘时间，用于节流。 */
    private val lastFlushMs: MutableMap<String, Long> = mutableMapOf()

    /** 上次 Running 事件发射时间（按任务），用于节流高频进度事件。 */
    private val runningEventLastEmitMs: MutableMap<String, Long> = mutableMapOf()

    /** 保护 segmentCache / lastFlushMs 的锁。 */
    private val segmentCacheLock = Any()

    /**
     * 执行真实文件下载。
     *
     * 流程包括下载源决策、远端元数据探测、分片规划、分片下载、合并和文件校验。
     */
    override suspend fun download(
        request: DownloadRequest,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // 先决定当前请求是否允许走真实 HTTP；不允许时直接回退到兜底实现。
        val sourceDecision = sourceResolver.resolve(request.sourcePolicy, request.url)
        if (sourceDecision.policy != DownloadSourcePolicy.DIRECT_HTTP) {
            fallbackDownloader?.download(request, control, onEvent) ?: onEvent(DownloadEvent.Failed(DownloadFailureCode.UNKNOWN, sourceDecision.reason))
            return@withContext
        }

        val probeResult = probeAndResolveTotalBytes(request, control, onEvent) ?: return@withContext
        val (meta, totalBytes) = probeResult

        val plannedSegments = preparePlannedSegments(request, totalBytes)
        val batchResult = executeBatches(request, meta, plannedSegments, totalBytes, control, onEvent)
        if (dispatchBatchOutcome(request, batchResult, totalBytes, control, onEvent)) {
            return@withContext
        }

        finalizeDownload(request, plannedSegments, batchResult.segmentResults, totalBytes, control, onEvent)
    }

    /** 基于元数据与历史分片记录生成本次下载的分片方案，并落盘。 */
    private fun preparePlannedSegments(request: DownloadRequest, totalBytes: Long): List<DownloadSegmentRecord> {
        request.targetFile.parentFile?.mkdirs()
        val taskTempDir = store.getTaskTempDir(request.taskId)
        if (!request.targetFile.exists()) request.targetFile.createNewFile()
        val existingSegments = store.readSegments(request.taskId)
        val plannedSegments = segmentPlanner.plan(
            taskId = request.taskId,
            tempDir = taskTempDir,
            totalBytes = totalBytes,
            requestedSegmentCount = REQUESTED_SEGMENT_COUNT,
            existingSegments = existingSegments,
        ).sortedBy { it.index }
        store.saveSegments(request.taskId, plannedSegments)
        // 同步加载内存缓存，后续 saveSegmentRecord 走缓存层，避免热路径全量 JSON 读写。
        synchronized(segmentCacheLock) {
            segmentCache[request.taskId] = plannedSegments.associateBy { it.segmentId }.toMutableMap()
            lastFlushMs[request.taskId] = System.currentTimeMillis()
        }
        return plannedSegments
    }

    /** 把批次结果归一化为停止/失败事件；返回 true 表示已发出终止事件，调用方应直接返回。 */
    private suspend fun dispatchBatchOutcome(
        request: DownloadRequest,
        batchResult: BatchResult,
        totalBytes: Long,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
    ): Boolean {
        if (batchResult.stoppedReason != null) {
            onEvent(
                DownloadEvent.Stopped(
                    reason = requireNotNull(batchResult.stoppedReason),
                    downloadedBytes = calculateAggregateDownloaded(request.taskId),
                    totalBytes = totalBytes,
                ),
            )
            return true
        }
        if (emitStoppedIfRequested(request, control, onEvent, totalBytes)) {
            return true
        }
        batchResult.failedResult?.let { failure ->
            onEvent(
                DownloadEvent.Failed(
                    code = failure.code ?: DownloadFailureCode.UNKNOWN,
                    message = failure.message ?: (failure.code?.displayText ?: DownloaderText.UNKNOWN_DOWNLOAD_FAILURE),
                    retryable = failure.code?.retryable ?: true,
                ),
            )
            return true
        }
        return false
    }

    /** 探测远端元数据、校验 ETag/Last-Modified 并解析总字节数。返回 null 表示已发送终止事件。 */
    private suspend fun probeAndResolveTotalBytes(
        request: DownloadRequest,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
    ): Pair<DownloadRemoteMeta, Long>? {
        if (emitStoppedIfRequested(request, control, onEvent, request.totalBytes)) {
            return null
        }
        onEvent(DownloadEvent.Waiting)
        val meta = try {
            probeRemoteMeta(request.url, control)
        } catch (e: SocketTimeoutException) {
            if (emitStoppedIfRequested(request, control, onEvent, request.totalBytes)) {
                return null
            }
            onEvent(DownloadEvent.Failed(DownloadFailureCode.NETWORK_TIMEOUT, e.message ?: DownloadFailureCode.NETWORK_TIMEOUT.displayText))
            return null
        } catch (e: IOException) {
            if (emitStoppedIfRequested(request, control, onEvent, request.totalBytes)) {
                return null
            }
            onEvent(DownloadEvent.Failed(DownloadFailureCode.NETWORK_INTERRUPTED, e.message ?: DownloadFailureCode.NETWORK_INTERRUPTED.displayText))
            return null
        }
        store.saveMeta(request.taskId, meta)
        onEvent(DownloadEvent.MetaReady(meta))

        // 如果远端文件已经变化，就终止当前续传，避免把不同版本文件拼到一起。
        if (request.eTag != null && meta.eTag != null && request.eTag != meta.eTag) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.REMOTE_FILE_CHANGED, DownloaderText.REMOTE_FILE_CHANGED_REDOWNLOAD, false))
            return null
        }
        if (request.lastModified != null && meta.lastModified != null && request.lastModified != meta.lastModified) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.REMOTE_FILE_CHANGED, DownloaderText.REMOTE_FILE_CHANGED_REDOWNLOAD, false))
            return null
        }

        val totalBytes = when {
            request.totalBytes > 0L -> request.totalBytes
            meta.contentLength > 0L -> meta.contentLength
            else -> 0L
        }
        if (emitStoppedIfRequested(request, control, onEvent, totalBytes)) {
            return null
        }
        return Pair(meta, totalBytes)
    }

    /** 分批执行分片下载，受 maxParallelSegments 控制并发度。 */
    private suspend fun executeBatches(
        request: DownloadRequest,
        meta: DownloadRemoteMeta,
        plannedSegments: List<DownloadSegmentRecord>,
        totalBytes: Long,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
    ): BatchResult {
        val eventMutex = Mutex()
        val segmentResults = mutableListOf<SegmentResult>()
        var failedResult: SegmentResult? = null
        var stoppedReason: DownloadStopReason? = null

        coroutineScope {
            for (batch in plannedSegments.chunked(maxParallelSegments.coerceAtLeast(1))) {
                val results = batch.map { segment ->
                    async(Dispatchers.IO) {
                        downloadSegmentWithRetry(request, meta, segment, control) { speed ->
                            eventMutex.withLock {
                                if (control.isStopRequested()) {
                                    return@withLock
                                }
                                // Running 事件按任务节流：终止事件自带最终字节数，中间精度损失无影响。
                                if (!shouldEmitRunningEvent(request.taskId)) {
                                    return@withLock
                                }
                                onEvent(
                                    DownloadEvent.Running(
                                        downloadedBytes = calculateAggregateDownloaded(request.taskId),
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed,
                                    ),
                                )
                            }
                        }
                    }
                }.awaitAll()

                val firstStopped = results.firstOrNull { it.stopReason != null }
                if (firstStopped != null) {
                    stoppedReason = firstStopped.stopReason
                    break
                }
                val firstFailure = results.firstOrNull { !it.success }
                if (firstFailure != null) {
                    failedResult = firstFailure
                    break
                }
                segmentResults += results
            }
        }
        return BatchResult(segmentResults, failedResult, stoppedReason)
    }

    /** 合并分片、校验文件并收口完成事件。 */
    private suspend fun finalizeDownload(
        request: DownloadRequest,
        plannedSegments: List<DownloadSegmentRecord>,
        segmentResults: List<SegmentResult>,
        totalBytes: Long,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
    ) {
        val finalFile = request.targetFile
        if (finalFile.exists()) finalFile.delete()
        // 合并前预留测试钩子，便于验证分片文件被破坏时的收口行为。
        beforeMergeHook?.invoke(plannedSegments, finalFile)
        val mergeOk = DownloadFileHelper.mergeSegments(plannedSegments, finalFile)
        if (emitStoppedIfRequested(request, control, onEvent, totalBytes)) {
            return
        }
        if (!mergeOk) {
            onEvent(DownloadEvent.Failed(DownloadFailureCode.MERGE_FAILED, DownloadFailureCode.MERGE_FAILED.displayText, false))
            return
        }

        // 合并完成后做长度和校验值验证，确保最终产物可信。
        val finalSize = finalFile.length()
        val verification = DownloadFileHelper.verifyFile(finalFile, totalBytes, request.checksumType, request.checksumValue)
        if (!verification.ok) {
            onEvent(DownloadEvent.Failed(verification.code, verification.message, verification.code.retryable))
            return
        }

        // 最终成功后把所有分片状态统一收口为已完成。
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

    /** 带重试策略地下载单个分片。 */
    private suspend fun downloadSegmentWithRetry(
        request: DownloadRequest,
        meta: DownloadRemoteMeta,
        segment: DownloadSegmentRecord,
        control: DownloadExecutionControl,
        onProgress: suspend (speedBytesPerSec: Long) -> Unit,
    ): SegmentResult {
        var attempt = 0
        while (attempt <= maxSegmentRetryCount) {
            control.currentStopReason()?.let { reason ->
                return SegmentResult(segment.segmentId, false, stopReason = reason, attempts = attempt)
            }
            val result = downloadSingleSegment(request, meta, segment, control, attempt, onProgress)
            if (result.success) {
                return result
            }
            if (result.stopReason != null) {
                return result
            }
            if (!(result.code?.retryable ?: false) || attempt >= maxSegmentRetryCount) {
                return result
            }
            attempt++
        }
        return SegmentResult(
            segment.segmentId,
            false,
            code = DownloadFailureCode.UNKNOWN,
            message = DownloaderText.SEGMENT_DOWNLOAD_FAILED,
            attempts = maxSegmentRetryCount,
        )
    }

    /** 下载单个分片，并在下载过程中持续写入分片状态。 */
    private suspend fun downloadSingleSegment(
        request: DownloadRequest,
        meta: DownloadRemoteMeta,
        segment: DownloadSegmentRecord,
        control: DownloadExecutionControl,
        attempt: Int,
        onProgress: suspend (speedBytesPerSec: Long) -> Unit,
    ): SegmentResult {
        val partFile = File(segment.tmpFilePath)
        if (!partFile.exists()) partFile.createNewFile()

        // 通过已有文件大小和历史记录计算续传起点。
        val existingBytes = maxOf(segment.downloadedBytes, partFile.length())
        val resumeOffset = if (existingBytes > 0L) segment.startByte + existingBytes else segment.startByte

        control.currentStopReason()?.let { reason ->
            saveSegmentRecord(request, segment, existingBytes, stopStatus(reason), retryCount = attempt)
            return SegmentResult(segment.segmentId, false, stopReason = reason, attempts = attempt)
        }

        if (existingBytes > 0L && !meta.supportsRange) {
            return SegmentResult(
                segment.segmentId,
                false,
                code = DownloadFailureCode.RANGE_NOT_SUPPORTED,
                message = DownloadFailureCode.RANGE_NOT_SUPPORTED.displayText,
                attempts = attempt,
            )
        }

        saveSegmentRecord(
            request,
            segment,
            existingBytes,
            if (existingBytes > 0L) DownloaderText.STATUS_RESUMING else DownloaderText.STATUS_WAITING,
            retryCount = attempt,
        )

        // 根据分片起止字节发起 Range 请求。
        val connection = openConnection(
            request.url,
            rangeStart = if (resumeOffset > 0L) resumeOffset else null,
            rangeEnd = if (segment.endByte >= segment.startByte) segment.endByte else null,
        )
        val removeInterrupt = control.registerInterrupt { connection.disconnect() }

        return try {
            transferSegmentBytes(connection, partFile, existingBytes, request, segment, attempt, control, onProgress)
        } catch (e: SocketTimeoutException) {
            handleSegmentError(e, existingBytes, partFile, request, segment, attempt, control)
        } catch (e: IOException) {
            handleSegmentError(e, existingBytes, partFile, request, segment, attempt, control)
        } finally {
            removeInterrupt()
            connection.disconnect()
        }
    }

    /** 在已建立连接的分片上完成响应码校验、流式写入与完整性校验。 */
    private suspend fun transferSegmentBytes(
        connection: HttpURLConnection,
        partFile: File,
        existingBytes: Long,
        request: DownloadRequest,
        segment: DownloadSegmentRecord,
        attempt: Int,
        control: DownloadExecutionControl,
        onProgress: suspend (speedBytesPerSec: Long) -> Unit,
    ): SegmentResult {
        control.currentStopReason()?.let { reason ->
            saveSegmentRecord(request, segment, existingBytes, stopStatus(reason), retryCount = attempt)
            return SegmentResult(segment.segmentId, false, stopReason = reason, attempts = attempt)
        }
        val codeError = interpretResponseCode(connection.responseCode, existingBytes, segment, attempt)
        if (codeError != null) {
            return codeError
        }

        // 从输入流写入分片文件，实时上报进度。
        RandomAccessFile(partFile, "rw").use { out ->
            if (existingBytes > 0L) out.seek(existingBytes) else out.setLength(0L)
            connection.inputStream.use { input ->
                val writeResult = writeSegmentToFile(input, out, existingBytes, request, segment, attempt, control, onProgress)
                if (writeResult != null) {
                    return writeResult
                }
            }
        }

        val completionError = checkSegmentCompletion(partFile, existingBytes, segment, request, attempt)
        if (completionError != null) {
            return completionError
        }
        return SegmentResult(segment.segmentId, true, attempts = attempt)
    }

    /** 按响应码归一化失败原因，返回 null 表示响应正常可以继续下载。 */
    private fun interpretResponseCode(code: Int, existingBytes: Long, segment: DownloadSegmentRecord, attempt: Int): SegmentResult? = when {
        existingBytes > 0L && code != HttpURLConnection.HTTP_PARTIAL -> SegmentResult(
            segment.segmentId,
            false,
            code = DownloadFailureCode.RANGE_NOT_SUPPORTED,
            message = DownloaderText.RANGE_RESPONSE_INVALID,
            attempts = attempt,
        )

        code in HTTP_SUCCESS_RANGE -> null

        // 416 Requested Range Not Satisfiable：续传 offset 越界（远端文件已变 / 已完整下载），
        // 删除本分片临时文件并标记可重试，下次重新从 0 下载，避免无限重试同一越界范围。
        code == HTTP_RANGE_NOT_SATISFIABLE -> {
            runCatching { File(segment.tmpFilePath).delete() }
            SegmentResult(
                segment.segmentId,
                false,
                code = DownloadFailureCode.HTTP_4XX,
                message = "HTTP 416 range not satisfiable, will retry from start",
                attempts = attempt,
            )
        }

        code in HTTP_CLIENT_ERROR_RANGE -> SegmentResult(
            segment.segmentId,
            false,
            code = DownloadFailureCode.HTTP_4XX,
            message = "HTTP $code",
            attempts = attempt,
        )

        code >= HTTP_SERVER_ERROR_THRESHOLD -> SegmentResult(
            segment.segmentId,
            false,
            code = DownloadFailureCode.HTTP_5XX,
            message = "HTTP $code",
            attempts = attempt,
        )

        else -> null
    }

    /** 从输入流持续写入分片文件，遇到停止请求时提前返回失败结果。 */
    private suspend fun writeSegmentToFile(
        input: java.io.InputStream,
        out: RandomAccessFile,
        existingBytes: Long,
        request: DownloadRequest,
        segment: DownloadSegmentRecord,
        attempt: Int,
        control: DownloadExecutionControl,
        onProgress: suspend (speedBytesPerSec: Long) -> Unit,
    ): SegmentResult? {
        val buffer = ByteArray(chunkBytes)
        var segmentDownloaded = existingBytes
        val startedAt = System.currentTimeMillis()
        var read = input.read(buffer)
        while (read >= 0) {
            control.currentStopReason()?.let { reason ->
                saveSegmentRecord(request, segment, segmentDownloaded, stopStatus(reason), retryCount = attempt)
                return SegmentResult(segment.segmentId, false, stopReason = reason, attempts = attempt)
            }
            out.write(buffer, 0, read)
            segmentDownloaded += read
            saveSegmentRecord(request, segment, segmentDownloaded, DownloaderText.STATUS_RUNNING, retryCount = attempt)
            val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            val speed = ((segmentDownloaded - existingBytes).coerceAtLeast(1L) * SPEED_CALCULATION_BASE / elapsedMs).coerceAtLeast(1L)
            onProgress(speed)
            read = input.read(buffer)
        }
        return null
    }

    /** 校验分片下载完整性，返回 null 表示通过。 */
    private fun checkSegmentCompletion(
        partFile: File,
        existingBytes: Long,
        segment: DownloadSegmentRecord,
        request: DownloadRequest,
        attempt: Int,
    ): SegmentResult? {
        val finalDownloaded = maxOf(existingBytes, partFile.length())
        val expectedLength = if (segment.endByte >= segment.startByte) (segment.endByte - segment.startByte + 1L) else finalDownloaded
        if (expectedLength > 0L && finalDownloaded < expectedLength) {
            saveSegmentRecord(request, segment, finalDownloaded, DownloaderText.STATUS_FAILED_INCOMPLETE, retryCount = attempt)
            return SegmentResult(
                segment.segmentId,
                false,
                code = DownloadFailureCode.FILE_INCOMPLETE,
                message = DownloaderText.SEGMENT_FILE_INCOMPLETE,
                attempts = attempt,
            )
        }
        saveSegmentRecord(request, segment, finalDownloaded, DownloaderText.STATUS_COMPLETED, retryCount = attempt)
        return null
    }

    /** 统一处理分片下载过程中的超时和 IO 异常。 */
    private fun handleSegmentError(
        e: Throwable,
        existingBytes: Long,
        partFile: File,
        request: DownloadRequest,
        segment: DownloadSegmentRecord,
        attempt: Int,
        control: DownloadExecutionControl,
    ): SegmentResult {
        val downloadedBytes = maxOf(existingBytes, partFile.takeIf { it.exists() }?.length() ?: 0L)
        val stopReason = control.currentStopReason()
        if (stopReason != null) {
            saveSegmentRecord(request, segment, downloadedBytes, stopStatus(stopReason), retryCount = attempt)
            return SegmentResult(segment.segmentId, false, stopReason = stopReason, attempts = attempt)
        }
        return when (e) {
            is SocketTimeoutException -> {
                saveSegmentRecord(request, segment, downloadedBytes, DownloaderText.STATUS_FAILED_TIMEOUT, retryCount = attempt)
                SegmentResult(
                    segment.segmentId,
                    false,
                    code = DownloadFailureCode.NETWORK_TIMEOUT,
                    message = e.message ?: DownloadFailureCode.NETWORK_TIMEOUT.displayText,
                    attempts = attempt,
                )
            }

            else -> {
                saveSegmentRecord(request, segment, downloadedBytes, DownloaderText.STATUS_FAILED_IO, retryCount = attempt)
                SegmentResult(
                    segment.segmentId,
                    false,
                    code = DownloadFailureCode.NETWORK_INTERRUPTED,
                    message = e.message ?: DownloadFailureCode.NETWORK_INTERRUPTED.displayText,
                    attempts = attempt,
                )
            }
        }
    }

    /** 聚合所有分片的已下载字节数，用于计算任务级进度。优先读内存缓存，避免热路径磁盘 IO。 */
    private fun calculateAggregateDownloaded(taskId: String): Long {
        val segments = synchronized(segmentCacheLock) {
            segmentCache[taskId]?.values?.toList() ?: store.readSegments(taskId)
        }
        return segments.sumOf { seg ->
            maxOf(seg.downloadedBytes, File(seg.tmpFilePath).takeIf { it.exists() }?.length() ?: 0L)
        }
    }

    /**
     * 保存单个分片的最新状态。
     *
     * 写入内存缓存，并按节流策略决定是否同步刷盘：
     * - 非运行态（完成/失败/暂停/取消等）立即刷盘；
     * - 运行态按 [runningFlushIntervalMs] 节流，避免每 32KB 一次全量 JSON 重写。
     */
    private fun saveSegmentRecord(request: DownloadRequest, segment: DownloadSegmentRecord, downloadedBytes: Long, status: String, retryCount: Int) {
        val now = System.currentTimeMillis()
        val updated = segment.copy(downloadedBytes = downloadedBytes, status = status, retryCount = retryCount, updatedAt = now)
        val shouldFlush = synchronized(segmentCacheLock) {
            val taskCache = segmentCache.getOrPut(request.taskId) { mutableMapOf() }
            taskCache[segment.segmentId] = updated
            val isRunning = status == DownloaderText.STATUS_RUNNING
            if (!isRunning) {
                lastFlushMs[request.taskId] = now
                true
            } else {
                val last = lastFlushMs[request.taskId] ?: 0L
                if (now - last >= runningFlushIntervalMs) {
                    lastFlushMs[request.taskId] = now
                    true
                } else {
                    false
                }
            }
        }
        if (shouldFlush) flushSegmentCache(request.taskId)
    }

    /** 把内存缓存中的分片状态同步刷盘。 */
    private fun flushSegmentCache(taskId: String) {
        val snapshot = synchronized(segmentCacheLock) {
            segmentCache[taskId]?.values?.sortedBy { it.index } ?: return
        }
        store.saveSegments(taskId, snapshot)
    }

    /**
     * 判断当前任务是否应发射 Running 事件。
     *
     * 按任务维度做时间节流，首个事件总是发射；[runningEventIntervalMs] 为 0 时关闭节流。
     */
    private fun shouldEmitRunningEvent(taskId: String): Boolean {
        if (runningEventIntervalMs <= 0L) {
            return true
        }
        val now = System.currentTimeMillis()
        return synchronized(segmentCacheLock) {
            val last = runningEventLastEmitMs[taskId]
            if (last == null || now - last >= runningEventIntervalMs) {
                runningEventLastEmitMs[taskId] = now
                true
            } else {
                false
            }
        }
    }

    /** 单个分片下载完成后的归一化结果。 */
    private data class SegmentResult(
        /** 分片唯一标识。 */
        val segmentId: String,
        /** 当前分片是否下载成功。 */
        val success: Boolean,
        /** 分片是否是因为业务层主动暂停或取消而终止。 */
        val stopReason: DownloadStopReason? = null,
        /** 分片失败时对应的失败码。 */
        val code: DownloadFailureCode? = null,
        /** 分片失败时的错误文案。 */
        val message: String? = null,
        /** 当前分片已经消耗的尝试次数。 */
        val attempts: Int = 0,
    )

    /** 分批执行后的聚合结果。 */
    private data class BatchResult(
        /** 本批次成功下载的分片结果列表。 */
        val segmentResults: List<SegmentResult>,
        /** 本批次首个失败的分片结果。 */
        val failedResult: SegmentResult?,
        /** 本批次因停止请求而终止的原因。 */
        val stoppedReason: DownloadStopReason?,
    )

    /** 在关键阶段检查当前任务是否已经收到停止请求。 */
    private suspend fun emitStoppedIfRequested(
        request: DownloadRequest,
        control: DownloadExecutionControl,
        onEvent: suspend (DownloadEvent) -> Unit,
        totalBytes: Long,
    ): Boolean {
        val reason = control.currentStopReason() ?: return false
        onEvent(DownloadEvent.Stopped(reason = reason, downloadedBytes = calculateKnownDownloadedBytes(request), totalBytes = totalBytes))
        return true
    }

    /** 计算当前任务已知的下载字节数，优先读取最新分片落盘结果。 */
    private fun calculateKnownDownloadedBytes(request: DownloadRequest): Long {
        val aggregateDownloaded = calculateAggregateDownloaded(request.taskId)
        return if (aggregateDownloaded > 0L) aggregateDownloaded else request.downloadedBytes
    }

    /** 将停止原因映射为分片持久化状态。 */
    private fun stopStatus(reason: DownloadStopReason): String = when (reason) {
        DownloadStopReason.PAUSED -> DownloaderText.STATUS_PAUSED
        DownloadStopReason.CANCELED -> DownloaderText.STATUS_CANCELED
    }

    /** 通过 HEAD 请求探测远端文件元数据。 */
    private fun probeRemoteMeta(url: String, control: DownloadExecutionControl): DownloadRemoteMeta {
        val headConnection = openConnection(url, head = true)
        val removeInterrupt = control.registerInterrupt { headConnection.disconnect() }
        return try {
            if (control.isStopRequested()) {
                throw IOException("download stopped before probe connect")
            }
            headConnection.connect()
            if (control.isStopRequested()) {
                throw IOException("download stopped after probe connect")
            }
            DownloadRemoteMeta(
                contentLength = headConnection.getHeaderFieldLong("Content-Length", -1L),
                eTag = headConnection.getHeaderField("ETag"),
                lastModified = headConnection.getHeaderField("Last-Modified"),
                supportsRange = headConnection.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true,
                mimeType = headConnection.contentType,
            )
        } finally {
            removeInterrupt()
            headConnection.disconnect()
        }
    }

    /** 按需构建 HTTP 连接，并在存在续传需求时补齐 Range 请求头。 */
    private fun openConnection(url: String, head: Boolean = false, rangeStart: Long? = null, rangeEnd: Long? = null): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = if (head) "HEAD" else "GET"
        requestHeaders.forEach(connection::setRequestProperty)
        connection.setRequestProperty("Accept", "*/*")
        if (rangeStart != null) {
            val value = if (rangeEnd != null && rangeEnd >= rangeStart) {
                "bytes=$rangeStart-$rangeEnd"
            } else {
                "bytes=$rangeStart-"
            }
            connection.setRequestProperty("Range", value)
        }
        // 固定鉴权头模式禁止自动重定向，避免认证信息被转发到非预期主机。
        connection.instanceFollowRedirects = requestHeaders.isEmpty()
        connection.doInput = true
        return connection
    }

    private companion object {
        /** 默认连接超时时间。 */
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000

        /** 默认读取超时时间。 */
        const val DEFAULT_READ_TIMEOUT_MS = 15_000

        /** 默认缓冲区大小。 */
        const val DEFAULT_CHUNK_BYTES = 32 * 1024

        /** 默认最大并行分片数。 */
        const val DEFAULT_MAX_PARALLEL_SEGMENTS = 2

        /** 默认最大分片重试次数。 */
        const val DEFAULT_MAX_SEGMENT_RETRY_COUNT = 2

        /** 运行态默认刷盘节流间隔：500ms，避免 32KB buffer 触发的全量 JSON 重写。 */
        const val DEFAULT_RUNNING_FLUSH_INTERVAL_MS = 500L

        /** Running 事件默认发射节流间隔：200ms，避免每 32KB 触发的事件风暴。 */
        const val DEFAULT_RUNNING_EVENT_INTERVAL_MS = 200L

        /** RFC 9110 token 字符集合，用于拒绝非法或可注入的 header 名称。 */
        val HTTP_HEADER_NAME_PATTERN = Regex("""^[!#$%&'*+.^_`|~0-9A-Za-z-]+$""")

        /** 请求的分段并发数。 */
        const val REQUESTED_SEGMENT_COUNT = 2

        /** HTTP 成功响应码范围。 */
        val HTTP_SUCCESS_RANGE = 200..206

        /** HTTP 客户端错误响应码范围。 */
        val HTTP_CLIENT_ERROR_RANGE = 400..499

        /** HTTP 服务端错误响应码起始值。 */
        const val HTTP_SERVER_ERROR_THRESHOLD = 500

        /** HTTP 416 Range Not Satisfiable，续传 offset 越界专用。 */
        const val HTTP_RANGE_NOT_SATISFIABLE = 416

        /** 速度计算基准毫秒数。 */
        const val SPEED_CALCULATION_BASE = 1000L
    }
}
