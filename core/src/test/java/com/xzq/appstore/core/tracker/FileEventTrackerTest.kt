package com.xzq.appstore.core.tracker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileEventTrackerTest {

    @Test
    fun `track writes event to local file`() {
        val file = Files.createTempDirectory("event-tracker").toFile().resolve("events.tsv")
        // 注入 Unconfined 作用域让落盘在调用线程内联完成，测试可同步断言文件内容。
        val tracker = FileEventTracker(file, writeScope = CoroutineScope(Dispatchers.Unconfined))

        tracker.track("download_start_demo")

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("download_start_demo"))
    }

    @Test
    fun `track 连续多次写入会保留全部事件`() {
        val file = Files.createTempDirectory("event-tracker-batch").toFile().resolve("events.tsv")
        val tracker = FileEventTracker(file, writeScope = CoroutineScope(Dispatchers.Unconfined))

        tracker.track("event_a")
        tracker.track("event_b")

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("event_a"))
        assertTrue(text.contains("event_b"))
    }

    @Test
    fun `track 在文件超过大小上限时会轮转并继续写入新文件`() {
        val dir = Files.createTempDirectory("event-tracker-rotate").toFile()
        val file = dir.resolve("events.tsv")
        val tracker = FileEventTracker(
            eventLogFile = file,
            writeScope = CoroutineScope(Dispatchers.Unconfined),
            // 上限设为 1 字节：第一次写入后必然超限，第二次写入前触发轮转。
            maxLogBytes = 1L,
        )

        tracker.track("before_rotate")
        tracker.track("after_rotate")

        // 旧内容整体挪入 .1，当前文件只包含轮转后的新事件。
        val rotated = dir.resolve("events.tsv.1")
        assertTrue(rotated.exists())
        assertTrue(rotated.readText(Charsets.UTF_8).contains("before_rotate"))
        assertTrue(file.readText(Charsets.UTF_8).contains("after_rotate"))
    }
}
