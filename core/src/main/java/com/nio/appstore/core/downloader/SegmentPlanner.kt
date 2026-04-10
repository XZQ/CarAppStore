package com.nio.appstore.core.downloader

import java.io.File
import java.util.UUID

class SegmentPlanner(
    /** 期望的默认分片大小，当前主要作为规划参考值。 */
    private val defaultChunkSizeBytes: Long = 2L * 1024L * 1024L,
    /** 允许的最小分片大小，避免把文件切得过碎。 */
    private val minSegmentSizeBytes: Long = 512L * 1024L,
) {
    /** 根据文件大小、期望并发度和已有分片信息生成分片计划。 */
    fun plan(
        taskId: String,
        tempDir: File,
        totalBytes: Long,
        requestedSegmentCount: Int = 1,
        existingSegments: List<DownloadSegmentRecord> = emptyList(),
    ): List<DownloadSegmentRecord> {
        // 冷启动恢复时优先复用已有分片，避免重复切片。
        if (existingSegments.isNotEmpty()) return existingSegments.sortedBy { it.index }
        if (totalBytes <= 0L) {
            // 文件大小未知时退化为单分片顺序下载。
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
        // 结合最小分片大小约束，避免切出过多无意义的小分片。
        val safeCount = maxOf(1, minOf(suggestedCount, (totalBytes / minSegmentSizeBytes).toInt().coerceAtLeast(1)))
        val segmentSize = maxOf(minSegmentSizeBytes, maxOf(defaultChunkSizeBytes, (totalBytes + safeCount - 1) / safeCount))

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
