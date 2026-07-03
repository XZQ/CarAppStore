package com.xzq.appstore.core.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EventReporterTest {
    @Test
    fun `NoOpEventReporter reports success`() {
        val result = NoOpEventReporter.report(listOf(TrackedEvent(1L, "x")))
        assertTrue(result)
    }

    @Test
    fun `FileEventTracker forwards tracked events to reporter`() {
        val file = Files.createTempDirectory("event-tracker").toFile().resolve("events.tsv")
        val recorded = mutableListOf<TrackedEvent>()
        val reporter =
            object : EventReporter {
                override fun report(events: List<TrackedEvent>): Boolean {
                    recorded.addAll(events)
                    return true
                }
            }
        val tracker = FileEventTracker(file, reporter, clock = { 1_000L })

        tracker.track("download_start_demo")

        assertEquals(1, recorded.size)
        assertEquals(1_000L, recorded[0].timestamp)
        assertEquals("download_start_demo", recorded[0].payload)
    }

    @Test
    fun `FileEventTracker swallows reporter failure without breaking local write`() {
        val file = Files.createTempDirectory("event-tracker").toFile().resolve("events.tsv")
        val reporter =
            object : EventReporter {
                override fun report(events: List<TrackedEvent>): Boolean = throw IllegalStateException("network down")
            }
        val tracker = FileEventTracker(file, reporter, clock = { 2_000L })

        tracker.track("install_ok_demo")

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("install_ok_demo"))
    }
}
