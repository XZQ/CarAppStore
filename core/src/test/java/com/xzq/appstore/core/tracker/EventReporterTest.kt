package com.xzq.appstore.core.tracker

import com.xzq.appstore.core.logger.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EventReporterTest {
    @Test
    fun `EventTracker routes debug output through AppLogger`() {
        val messages = mutableListOf<String>()
        val logger = object : AppLogger() {
            override fun d(tag: String, message: String) {
                messages += "$tag:$message"
            }
        }

        EventTracker(logger = logger).track("download_start_demo")

        assertEquals(listOf("EventTracker:download_start_demo"), messages)
    }

    @Test
    fun `FileEventTracker routes persistence failure through AppLogger`() {
        val directory = Files.createTempDirectory("event-tracker").toFile()
        val failures = mutableListOf<Throwable?>()
        val logger = object : AppLogger() {
            override fun d(tag: String, message: String) = Unit

            override fun w(tag: String, message: String, throwable: Throwable?) {
                failures += throwable
            }
        }

        FileEventTracker(directory, logger = logger).track("install_start_demo")

        assertEquals(1, failures.size)
        assertTrue(failures.single() != null)
    }

    @Test
    fun `NoOpEventReporter reports success`() {
        val result = NoOpEventReporter.report(listOf(TrackedEvent(1L, "x")))
        assertTrue(result)
    }

    @Test
    fun `FileEventTracker forwards tracked events to reporter`() {
        val file = Files.createTempDirectory("event-tracker").toFile().resolve("events.tsv")
        val recorded = mutableListOf<TrackedEvent>()
        val reporter = object : EventReporter {
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
        val warnings = mutableListOf<String>()
        val logger = object : AppLogger() {
            override fun d(tag: String, message: String) = Unit

            override fun w(tag: String, message: String, throwable: Throwable?) {
                warnings += message
            }
        }
        val reporter = object : EventReporter {
            override fun report(events: List<TrackedEvent>): Boolean = throw IllegalStateException("network down")
        }
        val tracker = FileEventTracker(file, reporter, clock = { 2_000L }, logger = logger)

        tracker.track("install_ok_demo")

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("install_ok_demo"))
        assertTrue(warnings.single().contains("network down"))
    }

    @Test
    fun `FileEventTracker warns when reporter rejects event batch`() {
        val file = Files.createTempDirectory("event-tracker").toFile().resolve("events.tsv")
        val warnings = mutableListOf<String>()
        val logger = object : AppLogger() {
            override fun d(tag: String, message: String) = Unit

            override fun w(tag: String, message: String, throwable: Throwable?) {
                warnings += message
            }
        }
        val reporter = object : EventReporter {
            override fun report(events: List<TrackedEvent>): Boolean = false
        }

        FileEventTracker(file, reporter, logger = logger).track("install_rejected_demo")

        assertTrue(warnings.single().contains("rejected"))
    }
}
