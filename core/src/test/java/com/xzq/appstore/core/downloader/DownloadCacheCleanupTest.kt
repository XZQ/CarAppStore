package com.xzq.appstore.core.downloader

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadCacheCleanupTest {
    @Test fun `cleanup only deletes generated files inside requested task`() {
        val root = Files.createTempDirectory("cache-cleanup").toFile()
        try {
            val store = DownloadStore(root)
            val task = store.getTaskTempDir("task-a")
            File(task, "part-0.tmp").writeText("part")
            File(task, "meta.json").writeText("{}")
            val other = File(store.getTaskTempDir("task-b"), "part-0.tmp").apply { writeText("other") }
            val unrelated = File(task, "keep.txt").apply { writeText("keep") }
            store.clearTask("task-a")
            assertFalse(File(task, "part-0.tmp").exists())
            assertFalse(File(task, "meta.json").exists())
            assertTrue(other.exists())
            assertTrue(unrelated.exists())
            assertThrows(IllegalArgumentException::class.java) { store.clearTask("../task-b") }
        } finally { root.deleteRecursively() }
    }
}
