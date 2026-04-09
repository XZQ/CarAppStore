package com.nio.appstore.core.downloader
import java.io.File
import java.util.UUID

class SegmentPlanner(
    private val defaultChunkSizeBytes: Long = 2L * 1024L * 1024L,
    private val minSegmentSizeBytes: Long = 512L * 1024L,
) {
    fun plan(
        taskId: String,
        tempDir: File,
        totalBytes: Long,
        requestedSegmentCount: Int = 1,
        existingSegments: List<DownloadSegmentRecord> = emptyList(),
    ): List<DownloadSegmentRecord> {
        if (existingSegments.isNotEmpty()) return existingSegments.sortedBy { it.index }
        if (totalBytes <= 0L) {
            val now = System.currentTimeMillis()
            val file = File(tempDir, "part-0.tmp")
            return listOf(
                DownloadSegmentRecord(
                    segmentId = UUID.randomUUID().toString(),
                    taskId = taskId,
                    index = 0,
                    startByte = 0L,
                    endByte = -1L,
                    downloadedBytes = 0L,
                    status = "WAITING",
                    tmpFilePath = file.absolutePath,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        val suggestedCount = when {
            requestedSegmentCount > 1 -> requestedSegmentCount
            totalBytes >= 16L * 1024L * 1024L -> 4
            totalBytes >= 8L * 1024L * 1024L -> 3
            totalBytes >= 4L * 1024L * 1024L -> 2
            else -> 1
        }
        val safeCount = maxOf(1, minOf(suggestedCount, (totalBytes / minSegmentSizeBytes).toInt().coerceAtLeast(1)))
        val segmentSize = maxOf(minSegmentSizeBytes, (totalBytes + safeCount - 1) / safeCount)

        val now = System.currentTimeMillis()
        val result = mutableListOf<DownloadSegmentRecord>()
        var start = 0L
        var index = 0
        while (start < totalBytes) {
            val end = minOf(totalBytes - 1L, start + segmentSize - 1L)
            val file = File(tempDir, "part-$index.tmp")
            result += DownloadSegmentRecord(
                segmentId = UUID.randomUUID().toString(),
                taskId = taskId,
                index = index,
                startByte = start,
                endByte = end,
                downloadedBytes = 0L,
                status = "WAITING",
                tmpFilePath = file.absolutePath,
                createdAt = now,
                updatedAt = now,
            )
            start = end + 1L
            index++
        }
        return result
    }
}
