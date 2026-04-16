package com.nio.appstore.core.downloader

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class RealFileDownloaderTest {

    @Test
    fun `download 在进入分片下载前收到取消后会快速停止`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = SLOW_HEAD_DELAY_MS,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 7 },
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            val control = DownloadExecutionControl()

            val job = async {
                fixture.downloader.download(fixture.request, control) { event ->
                    events += event
                }
            }

            delay(PROBE_SETTLE_DELAY_MS)
            control.requestCancel()

            withTimeout(FAST_STOP_TIMEOUT_MS) {
                job.await()
            }

            assertFalse(events.any { it is DownloadEvent.Running })
            val stopped = events.last() as DownloadEvent.Stopped
            assertEquals(DownloadStopReason.CANCELED, stopped.reason)
            assertEquals(0L, stopped.downloadedBytes)
            assertTrue(fixture.store.readSegments(fixture.request.taskId).isEmpty())
        }
    }

    @Test
    fun `download 在响应体读取阶段收到取消后会输出取消停止事件并标记分片`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = SLOW_BODY_DELAY_MS,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { index -> (index % 97).toByte() },
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            val control = DownloadExecutionControl()
            val canceled = AtomicBoolean(false)

            fixture.downloader.download(fixture.request, control) { event ->
                events += event
                if (event is DownloadEvent.Running && canceled.compareAndSet(false, true)) {
                    control.requestCancel()
                }
            }

            val stopped = events.last() as DownloadEvent.Stopped
            assertEquals(DownloadStopReason.CANCELED, stopped.reason)
            assertTrue(stopped.downloadedBytes > 0L)
            assertFalse(events.any { it is DownloadEvent.Completed })
            val segments = fixture.store.readSegments(fixture.request.taskId)
            assertTrue(segments.isNotEmpty())
            assertTrue(segments.any { it.status == DownloaderText.STATUS_CANCELED })
        }
    }

    @Test
    fun `download 在读取超时失败时会保留已下载分片进度`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 3 },
            stallAfterFirstChunkMs = SOCKET_TIMEOUT_MS.toLong() + EXTRA_STALL_BUFFER_MS,
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())

            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }

            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.NETWORK_TIMEOUT, failed.code)
            val segments = fixture.store.readSegments(fixture.request.taskId)
            assertTrue(segments.isNotEmpty())
            val segment = segments.first()
            assertEquals(DownloaderText.STATUS_FAILED_TIMEOUT, segment.status)
            assertEquals(SERVER_WRITE_CHUNK_BYTES.toLong(), segment.downloadedBytes)
        }
    }

    @Test
    fun `download 在网络中断失败时会保留已下载分片进度`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 5 },
            closeAfterFirstChunk = true,
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())

            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }

            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.NETWORK_INTERRUPTED, failed.code)
            val segments = fixture.store.readSegments(fixture.request.taskId)
            assertTrue(segments.isNotEmpty())
            val segment = segments.first()
            assertEquals(DownloaderText.STATUS_FAILED_IO, segment.status)
            assertEquals(SERVER_WRITE_CHUNK_BYTES.toLong(), segment.downloadedBytes)
        }
    }

    @Test
    fun `download 在服务端返回 4xx 时会归类为 HTTP_4XX`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 9 },
            getStatusCode = 404,
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.HTTP_4XX, failed.code)
        }
    }

    @Test
    fun `download 在服务端返回 5xx 时会归类为 HTTP_5XX`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 11 },
            getStatusCode = 503,
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.HTTP_5XX, failed.code)
        }
    }

    @Test
    fun `download 在续传时收到非 206 响应会归类为 RANGE_NOT_SUPPORTED`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 13 },
            ignoreRangeRequest = true,
            requestDownloadedBytes = PARTIAL_DOWNLOADED_BYTES,
            requestTotalBytes = TEST_TOTAL_BYTES,
        )

        fixture.use {
            fixture.primePartialSegment(PARTIAL_DOWNLOADED_BYTES, TEST_TOTAL_BYTES)
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.RANGE_NOT_SUPPORTED, failed.code)
        }
    }

    @Test
    fun `download 在文件校验不匹配时会归类为 CHECKSUM_MISMATCH`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 17 },
            requestChecksumType = "SHA-256",
            requestChecksumValue = sha256(ByteArray(TEST_TOTAL_BYTES.toInt()) { 99 }),
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.CHECKSUM_MISMATCH, failed.code)
            assertTrue(fixture.request.targetFile.exists())
        }
    }

    @Test
    fun `download 在 ETag 不一致时会归类为 REMOTE_FILE_CHANGED`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 19 },
            requestETag = "etag-old",
            headETag = "etag-new",
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.REMOTE_FILE_CHANGED, failed.code)
            assertFalse(events.any { it is DownloadEvent.Running })
        }
    }

    @Test
    fun `download 在 Last-Modified 不一致时会归类为 REMOTE_FILE_CHANGED`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(TEST_TOTAL_BYTES.toInt()) { 23 },
            requestLastModified = "Mon, 01 Jan 2024 00:00:00 GMT",
            headLastModified = "Tue, 02 Jan 2024 00:00:00 GMT",
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.REMOTE_FILE_CHANGED, failed.code)
            assertFalse(events.any { it is DownloadEvent.Running })
        }
    }

    @Test
    fun `download 在服务端提前结束响应体时会归类为 FILE_INCOMPLETE`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(INCOMPLETE_BODY_BYTES.toInt()) { 29 },
            headContentLength = TEST_TOTAL_BYTES,
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.FILE_INCOMPLETE, failed.code)
            val segments = fixture.store.readSegments(fixture.request.taskId)
            assertTrue(segments.isNotEmpty())
            assertEquals(DownloaderText.STATUS_FAILED_INCOMPLETE, segments.first().status)
        }
    }

    @Test
    fun `download 在合并前分片文件丢失时会归类为 MERGE_FAILED`() = runBlocking {
        val fixture = TestFixture(
            headDelayMs = 0L,
            bodyChunkDelayMs = 0L,
            payload = ByteArray(MERGE_TEST_TOTAL_BYTES.toInt()) { 31 },
            beforeMergeHook = { segments, _ ->
                File(segments.first().tmpFilePath).delete()
            },
        )

        fixture.use {
            val events = Collections.synchronizedList(mutableListOf<DownloadEvent>())
            fixture.downloader.download(fixture.request, DownloadExecutionControl()) { event ->
                events += event
            }
            val failed = events.last() as DownloadEvent.Failed
            assertEquals(DownloadFailureCode.MERGE_FAILED, failed.code)
        }
    }

    /** 下载器测试专用的依赖集合。 */
    private class TestFixture(
        /** HEAD 请求的响应延迟。 */
        headDelayMs: Long,
        /** GET 响应每个分块之间的延迟。 */
        bodyChunkDelayMs: Long,
        /** 服务器返回的 APK 内容。 */
        payload: ByteArray,
        /** 首个分块写出后额外停顿的时长。 */
        stallAfterFirstChunkMs: Long = 0L,
        /** 首个分块写出后是否立刻断开连接。 */
        closeAfterFirstChunk: Boolean = false,
        /** GET 请求固定返回的状态码。 */
        getStatusCode: Int = 200,
        /** 是否在 HEAD 中声明支持 Range。 */
        supportRangeHeader: Boolean = true,
        /** 收到 Range 请求后是否故意忽略并返回完整内容。 */
        ignoreRangeRequest: Boolean = false,
        /** HEAD 中声明的内容长度。 */
        headContentLength: Long = payload.size.toLong(),
        /** HEAD 中返回的 ETag。 */
        headETag: String? = null,
        /** HEAD 中返回的 Last-Modified。 */
        headLastModified: String? = null,
        /** 请求中声明的已下载字节数。 */
        requestDownloadedBytes: Long = 0L,
        /** 请求中声明的总字节数。 */
        requestTotalBytes: Long = 0L,
        /** 请求中声明的校验算法。 */
        requestChecksumType: String? = null,
        /** 请求中声明的校验值。 */
        requestChecksumValue: String? = null,
        /** 请求中声明的 ETag。 */
        requestETag: String? = null,
        /** 请求中声明的 Last-Modified。 */
        requestLastModified: String? = null,
        /** 合并前测试钩子。 */
        beforeMergeHook: ((segments: List<DownloadSegmentRecord>, finalFile: File) -> Unit)? = null,
    ) : AutoCloseable {
        /** 测试工作目录。 */
        private val workDir = Files.createTempDirectory("real-file-downloader-test").toFile()

        /** 下载元数据与分片存储。 */
        val store = DownloadStore(workDir)

        /** 本地 HTTP 测试服务器。 */
        val server = TestHttpServer(
            headDelayMs = headDelayMs,
            bodyChunkDelayMs = bodyChunkDelayMs,
            payload = payload,
            stallAfterFirstChunkMs = stallAfterFirstChunkMs,
            closeAfterFirstChunk = closeAfterFirstChunk,
            getStatusCode = getStatusCode,
            supportRangeHeader = supportRangeHeader,
            ignoreRangeRequest = ignoreRangeRequest,
            headContentLength = headContentLength,
            headETag = headETag,
            headLastModified = headLastModified,
        )

        /** 被测下载器实例。 */
        val downloader = RealFileDownloader(
            store = store,
            sourceResolver = DownloadSourceResolver(
                DownloadSourceResolverConfig(
                    defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
                    allowMockSource = false,
                    allowDirectHttp = true,
                )
            ),
            connectTimeoutMs = SOCKET_TIMEOUT_MS,
            readTimeoutMs = SOCKET_TIMEOUT_MS,
            chunkBytes = TEST_CHUNK_BYTES,
            maxParallelSegments = 1,
            maxSegmentRetryCount = 0,
            beforeMergeHook = beforeMergeHook,
        )

        /** 当前测试下载请求。 */
        val request = DownloadRequest(
            taskId = TEST_TASK_ID,
            appId = TEST_APP_ID,
            url = server.url,
            targetFile = store.getFinalDir().resolve("demo.apk"),
            downloadedBytes = requestDownloadedBytes,
            totalBytes = requestTotalBytes,
            checksumType = requestChecksumType,
            checksumValue = requestChecksumValue,
            eTag = requestETag,
            lastModified = requestLastModified,
            sourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
        )

        /** 为续传测试预置已有分片和临时文件。 */
        fun primePartialSegment(downloadedBytes: Long, totalBytes: Long) {
            val tempDir = store.getTaskTempDir(TEST_TASK_ID)
            val now = System.currentTimeMillis()
            val partFile = File(tempDir, "part-0.tmp").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(downloadedBytes.toInt()) { 1 })
            }
            store.saveSegments(
                TEST_TASK_ID,
                listOf(
                    DownloadSegmentRecord(
                        segmentId = "segment-0",
                        taskId = TEST_TASK_ID,
                        index = 0,
                        startByte = 0L,
                        endByte = totalBytes - 1L,
                        downloadedBytes = downloadedBytes,
                        status = DownloaderText.STATUS_RUNNING,
                        tmpFilePath = partFile.absolutePath,
                        retryCount = 0,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            )
        }

        override fun close() {
            server.close()
            workDir.deleteRecursively()
        }
    }

    /** 慢速响应场景使用的本地 HTTP 服务。 */
    private class TestHttpServer(
        /** HEAD 请求的响应延迟。 */
        private val headDelayMs: Long,
        /** GET 响应每个分块之间的延迟。 */
        private val bodyChunkDelayMs: Long,
        /** 返回给客户端的文件内容。 */
        private val payload: ByteArray,
        /** 首个分块写出后额外停顿的时长。 */
        private val stallAfterFirstChunkMs: Long,
        /** 首个分块写出后是否立刻断开连接。 */
        private val closeAfterFirstChunk: Boolean,
        /** GET 请求固定返回的状态码。 */
        private val getStatusCode: Int,
        /** 是否在 HEAD 中声明支持 Range。 */
        private val supportRangeHeader: Boolean,
        /** 收到 Range 请求后是否故意忽略并返回完整内容。 */
        private val ignoreRangeRequest: Boolean,
        /** HEAD 中声明的内容长度。 */
        private val headContentLength: Long,
        /** HEAD 中返回的 ETag。 */
        private val headETag: String?,
        /** HEAD 中返回的 Last-Modified。 */
        private val headLastModified: String?,
    ) : AutoCloseable {
        /** 测试服务端监听 socket。 */
        private val serverSocket = ServerSocket(0)

        /** 后台接受连接的线程。 */
        private val acceptThread = Thread {
            while (!serverSocket.isClosed) {
                try {
                    val socket = serverSocket.accept()
                    handle(socket)
                } catch (_: IOException) {
                    if (serverSocket.isClosed) {
                        return@Thread
                    }
                }
            }
        }

        /** 当前服务地址。 */
        val url: String = "http://127.0.0.1:${serverSocket.localPort}/demo.apk"

        init {
            acceptThread.isDaemon = true
            acceptThread.start()
        }

        override fun close() {
            serverSocket.close()
            acceptThread.join(1_000L)
        }

        /** 统一处理 HEAD 和 GET 请求。 */
        private fun handle(socket: Socket) {
            socket.use { client ->
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                val headers = readHeaders(reader)
                when (requestLine.substringBefore(' ').uppercase()) {
                    "HEAD" -> handleHead(client.getOutputStream())
                    "GET" -> handleGet(client, client.getOutputStream(), headers["Range"])
                    else -> writeHeaders(
                        output = client.getOutputStream(),
                        statusLine = "HTTP/1.1 405 Method Not Allowed",
                        headers = mapOf("Content-Length" to "0"),
                    )
                }
            }
        }

        /** 模拟带延迟的元数据探测响应。 */
        private fun handleHead(output: OutputStream) {
            if (headDelayMs > 0L) Thread.sleep(headDelayMs)
            writeHeaders(
                output = output,
                statusLine = "HTTP/1.1 200 OK",
                headers = buildMap {
                    put("Content-Length", headContentLength.toString())
                    put("Content-Type", "application/vnd.android.package-archive")
                    if (supportRangeHeader) {
                        put("Accept-Ranges", "bytes")
                    }
                    headETag?.let { put("ETag", it) }
                    headLastModified?.let { put("Last-Modified", it) }
                },
            )
        }

        /** 模拟支持 Range 且可慢速输出的文件下载响应。 */
        private fun handleGet(client: Socket, output: OutputStream, rangeHeader: String?) {
            if (headDelayMs > 0L) Thread.sleep(headDelayMs)
            if (getStatusCode !in 200..206) {
                writeHeaders(
                    output = output,
                    statusLine = "HTTP/1.1 $getStatusCode Error",
                    headers = mapOf("Content-Length" to "0"),
                )
                return
            }
            val effectiveRangeHeader = rangeHeader?.takeUnless { ignoreRangeRequest }
            val range = parseRange(effectiveRangeHeader, payload.size.toLong())
            val body = payload.copyOfRange(range.first.toInt(), range.last.toInt() + 1)
            writeHeaders(
                output = output,
                statusLine = if (effectiveRangeHeader != null) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK",
                headers = buildMap {
                    put("Accept-Ranges", "bytes")
                    put("Content-Type", "application/vnd.android.package-archive")
                    put("Content-Length", body.size.toString())
                    if (effectiveRangeHeader != null) {
                        put("Content-Range", "bytes ${range.first}-${range.last}/${payload.size}")
                    }
                },
            )
            try {
                var offset = 0
                while (offset < body.size) {
                    val nextOffset = minOf(offset + SERVER_WRITE_CHUNK_BYTES, body.size)
                    output.write(body, offset, nextOffset - offset)
                    output.flush()
                    offset = nextOffset
                    if (offset == nextOffset && offset == SERVER_WRITE_CHUNK_BYTES) {
                        if (stallAfterFirstChunkMs > 0L) {
                            Thread.sleep(stallAfterFirstChunkMs)
                        }
                        if (closeAfterFirstChunk) {
                            client.setSoLinger(true, 0)
                            return
                        }
                    }
                    if (offset < body.size && bodyChunkDelayMs > 0L) {
                        Thread.sleep(bodyChunkDelayMs)
                    }
                }
            } catch (_: IOException) {
                // 客户端主动断开连接属于测试期望路径，这里不额外抛出。
            }
        }

        /** 读取 HTTP 请求头。 */
        private fun readHeaders(reader: BufferedReader): Map<String, String> {
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val separatorIndex = line.indexOf(':')
                if (separatorIndex <= 0) continue
                val key = line.substring(0, separatorIndex).trim()
                val value = line.substring(separatorIndex + 1).trim()
                headers[key] = value
            }
            return headers
        }

        /** 输出简单的 HTTP 响应头。 */
        private fun writeHeaders(
            output: OutputStream,
            statusLine: String,
            headers: Map<String, String>,
        ) {
            val rawHeaders = buildString {
                append(statusLine).append("\r\n")
                headers.forEach { (key, value) ->
                    append(key).append(": ").append(value).append("\r\n")
                }
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(rawHeaders.toByteArray())
            output.flush()
        }

        /** 解析单段 Range 请求头。 */
        private fun parseRange(header: String?, totalBytes: Long): LongRange {
            if (header.isNullOrBlank()) return 0L..(totalBytes - 1L)
            val rawValue = header.removePrefix("bytes=").trim()
            val parts = rawValue.split("-", limit = 2)
            val start = parts.firstOrNull()?.toLongOrNull() ?: 0L
            val end = parts.getOrNull(1)?.toLongOrNull() ?: (totalBytes - 1L)
            return start..minOf(end, totalBytes - 1L)
        }
    }

    private companion object {
        /** 测试应用标识。 */
        const val TEST_APP_ID = "demo.app"

        /** 测试任务标识。 */
        const val TEST_TASK_ID = "download-demo-app"

        /** 测试文件总大小。 */
        const val TEST_TOTAL_BYTES = 64 * 1024L

        /** 合并失败测试使用的双分片文件大小。 */
        const val MERGE_TEST_TOTAL_BYTES = 1_024 * 1_024L

        /** 续传场景使用的已下载字节数。 */
        const val PARTIAL_DOWNLOADED_BYTES = 8 * 1024L

        /** 提前结束响应体场景使用的实际输出长度。 */
        const val INCOMPLETE_BODY_BYTES = 16 * 1024L

        /** 下载器每次读取的缓冲区大小。 */
        const val TEST_CHUNK_BYTES = 1024

        /** 服务端每次写出的分块大小。 */
        const val SERVER_WRITE_CHUNK_BYTES = 1024

        /** HEAD 延迟场景下服务端的阻塞时间。 */
        const val SLOW_HEAD_DELAY_MS = 3_000L

        /** GET 慢速输出场景下每个分块的延迟。 */
        const val SLOW_BODY_DELAY_MS = 80L

        /** 下载器 socket 超时配置。 */
        const val SOCKET_TIMEOUT_MS = 5_000

        /** 取消后期望的快速停止超时时间。 */
        const val FAST_STOP_TIMEOUT_MS = 1_500L

        /** 取消前留给 HEAD 探测进入阻塞区的等待时间。 */
        const val PROBE_SETTLE_DELAY_MS = 200L

        /** 让读取超时测试稳定命中的额外等待缓冲。 */
        const val EXTRA_STALL_BUFFER_MS = 300

        /** 计算测试用 SHA-256 值。 */
        fun sha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}
