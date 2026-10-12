package com.xzq.appstore.core.downloader

import java.io.File

/** 校验落盘分片的身份、完整范围和受控路径，禁止把记录中的任意路径交给文件 IO。 */
internal object DownloadSegmentValidator {
    fun isValid(taskId: String, tempDir: File, segments: List<DownloadSegmentRecord>, totalBytes: Long? = null): Boolean = runCatching {
        if (segments.isEmpty()) return@runCatching true
        val ordered = segments.sortedBy { it.index }
        if (ordered.map { it.segmentId }.toSet().size != ordered.size) return@runCatching false
        var nextStart = 0L
        ordered.forEachIndexed { index, segment ->
            val expectedFile = File(tempDir, "part-$index.tmp").absoluteFile
            if (segment.taskId != taskId || segment.segmentId.isBlank() || segment.index != index ||
                File(segment.tmpFilePath).absoluteFile != expectedFile || expectedFile.canonicalFile != expectedFile ||
                segment.startByte != nextStart || segment.downloadedBytes < 0L || segment.retryCount < 0) return@runCatching false
            if (segment.endByte == -1L && ordered.size == 1 && segment.startByte == 0L) {
                return@runCatching totalBytes == null || totalBytes <= 0L
            }
            if (segment.endByte < segment.startByte || segment.endByte == Long.MAX_VALUE ||
                segment.downloadedBytes > segment.endByte - segment.startByte + 1L) return@runCatching false
            nextStart = segment.endByte + 1L
        }
        totalBytes == null || totalBytes <= 0L || nextStart == totalBytes
    }.getOrDefault(false)
}
