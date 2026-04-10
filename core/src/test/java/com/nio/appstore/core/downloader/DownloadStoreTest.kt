package com.nio.appstore.core.downloader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DownloadStoreTest {

    @Test
    fun `readMeta 和 readSegments 会迁移旧 meta 文件`() {
        val baseDir = Files.createTempDirectory("download-store-test").toFile()
        val store = DownloadStore(baseDir)
        val metaFile = store.getTaskMetaFile("task-1")
        metaFile.parentFile?.mkdirs()
        metaFile.writeText(
            """
            {
              "contentLength": 1024,
              "eTag": "etag-1",
              "lastModified": "yesterday",
              "supportsRange": true,
              "mimeType": "application/vnd.android.package-archive",
              "segments": [
                {
                  "segmentId": "seg-1",
                  "taskId": "task-1",
                  "index": 0,
                  "startByte": 0,
                  "endByte": 1023,
                  "downloadedBytes": 256,
                  "status": "RUNNING",
                  "tmpFilePath": "/tmp/seg-1.tmp",
                  "retryCount": 1,
                  "createdAt": 11,
                  "updatedAt": 12
                }
              ]
            }
            """.trimIndent()
        )

        val meta = store.readMeta("task-1")
        val segments = store.readSegments("task-1")
        val persisted = JSONObject(metaFile.readText())

        assertEquals(1024L, meta?.contentLength)
        assertTrue(meta?.supportsRange == true)
        assertEquals(1, segments.size)
        assertEquals("seg-1", segments.first().segmentId)
        assertEquals(1, persisted.optInt("schemaVersion"))
    }

    @Test
    fun `saveSegments 会保留分片内容`() {
        val baseDir = Files.createTempDirectory("download-store-save-test").toFile()
        val store = DownloadStore(baseDir)
        val segments = listOf(
            DownloadSegmentRecord(
                segmentId = "seg-2",
                taskId = "task-2",
                index = 1,
                startByte = 100,
                endByte = 200,
                downloadedBytes = 50,
                status = "PAUSED",
                tmpFilePath = "/tmp/seg-2.tmp",
                retryCount = 2,
                createdAt = 20,
                updatedAt = 21,
            )
        )

        store.saveMeta(
            taskId = "task-2",
            meta = DownloadRemoteMeta(contentLength = 300, supportsRange = true),
        )
        store.saveSegments("task-2", segments)

        val savedSegments = store.readSegments("task-2")
        assertEquals(1, savedSegments.size)
        assertEquals("PAUSED", savedSegments.first().status)
    }
}
