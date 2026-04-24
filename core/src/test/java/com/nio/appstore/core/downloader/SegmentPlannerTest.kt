package com.nio.appstore.core.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SegmentPlannerTest 验证分片规划器的各分支逻辑。
 */
class SegmentPlannerTest {

    /** 测试用的临时目录。 */
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "segment-planner-test")

    /** 被测分片规划器，使用默认参数。 */
    private val planner = SegmentPlanner()

    /** 已有分片时直接返回并按 index 排序，不重新规划。 */
    @Test
    fun testPlanWithExistingSegments_returnsExistingSorted() {
        val existing = listOf(
            createSegment(index = 2),
            createSegment(index = 0),
            createSegment(index = 1),
        )
        val result = planner.plan("task-1", tempDir, 10L * 1024L * 1024L, existingSegments = existing)
        // 返回的是按 index 升序排列的已有分片
        assertEquals(3, result.size)
        assertEquals(0, result[0].index)
        assertEquals(1, result[1].index)
        assertEquals(2, result[2].index)
    }

    /** 文件大小未知（<=0）时退化为单分片顺序下载。 */
    @Test
    fun testPlanWithUnknownFileSize_returnsSingleSegment() {
        val result = planner.plan("task-2", tempDir, totalBytes = -1L)
        assertEquals(1, result.size)
        assertEquals(0, result[0].index)
        assertEquals(-1L, result[0].endByte)
    }

    /** 大文件（>=16MB）触发 4 分片策略。 */
    @Test
    fun testPlanWithLargeFile_returnsMultipleSegments() {
        val largeFileSize = 64L * 1024L * 1024L // 64MB
        val result = planner.plan("task-3", tempDir, largeFileSize)
        assertTrue("大文件应拆成多个分片", result.size >= 2)
        // 验证分片覆盖完整文件范围
        assertEquals(0L, result.first().startByte)
        assertEquals(largeFileSize - 1L, result.last().endByte)
    }

    /** 小文件（<4MB）只产生 1 个分片。 */
    @Test
    fun testPlanWithSmallFile_returnsOneSegment() {
        val smallFileSize = 2L * 1024L * 1024L // 2MB
        val result = planner.plan("task-4", tempDir, smallFileSize)
        assertEquals(1, result.size)
        assertEquals(smallFileSize - 1L, result[0].endByte)
    }

    /** 最小分片大小约束生效，不会切出过多的无用小分片。 */
    @Test
    fun testPlanMinSegmentSizeConstraint() {
        // 使用极小 minSegmentSize 配合强制高并发，验证分片数被合理限制
        val tinyPlanner = SegmentPlanner(
            defaultChunkSizeBytes = 1L,
            minSegmentSizeBytes = 1L * 1024L * 1024L, // 最小 1MB
        )
        // 请求 10 个分片，但文件只有 3MB
        val result = tinyPlanner.plan("task-5", tempDir, 3L * 1024L * 1024L, requestedSegmentCount = 10)
        // 受 minSegmentSize 约束，最多 3 个分片
        assertTrue("分片数应受最小分片约束", result.size <= 3)
        assertTrue("至少 1 个分片", result.size >= 1)
    }

    /** 构造测试用 DownloadSegmentRecord。 */
    private fun createSegment(index: Int): DownloadSegmentRecord {
        return DownloadSegmentRecord(
            segmentId = "seg-$index",
            taskId = "task-1",
            index = index,
            startByte = 0L,
            endByte = 100L,
            downloadedBytes = 0L,
            status = "WAITING",
            tmpFilePath = "/tmp/part-$index.tmp",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }
}
