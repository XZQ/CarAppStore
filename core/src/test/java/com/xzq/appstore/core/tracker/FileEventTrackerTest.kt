package com.xzq.appstore.core.tracker

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileEventTrackerTest {

    @Test
    fun `track writes event to local file`() {
        val file = Files.createTempDirectory("event-tracker").toFile().resolve("events.tsv")
        val tracker = FileEventTracker(file)

        tracker.track("download_start_demo")

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("download_start_demo"))
    }
}
