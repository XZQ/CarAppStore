package com.xzq.appstore.core.downloader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
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
                  "tmpFilePath": "${File(metaFile.parentFile, "part-0.tmp").absolutePath.replace("\\", "\\\\")}",
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
                index = 0,
                startByte = 0,
                endByte = 200,
                downloadedBytes = 50,
                status = "PAUSED",
                tmpFilePath = File(store.getTaskTempDir("task-2"), "part-0.tmp").absolutePath,
                retryCount = 2,
                createdAt = 20,
                updatedAt = 21,
            )
        )

        store.saveMeta(taskId = "task-2", meta = DownloadRemoteMeta(contentLength = 300, supportsRange = true))
        store.saveSegments("task-2", segments)

        val savedSegments = store.readSegments("task-2")
        assertEquals(1, savedSegments.size)
        assertEquals("PAUSED", savedSegments.first().status)
    }

    @Test
    fun `getTaskTempDir 合法任务目录始终位于 temp 根目录内`() {
        val baseDir = Files.createTempDirectory("download-store-path-test").toFile()
        val taskDir = DownloadStore(baseDir).getTaskTempDir("download-com.example.app")

        assertTrue(taskDir.canonicalFile.toPath().startsWith(File(baseDir, "temp").canonicalFile.toPath()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getTaskTempDir 拒绝路径穿越任务标识`() {
        DownloadStore(Files.createTempDirectory("download-store-traversal-test").toFile()).getTaskTempDir("download-safe/../../escape")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getTaskTempDir 拒绝反斜杠任务标识`() {
        DownloadStore(Files.createTempDirectory("download-store-backslash-test").toFile()).getTaskTempDir("download-safe\\..\\escape")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getTaskTempDir 拒绝超长任务标识`() {
        DownloadStore(Files.createTempDirectory("download-store-long-id-test").toFile()).getTaskTempDir("a".repeat(MAX_TASK_ID_LENGTH + 1))
    }

    @Test
    fun `磁盘分片指向任务外文件时只清理受控缓存并保留外部文件`() {
        val baseDir = Files.createTempDirectory("download-store-invalid-record").toFile()
        val store = DownloadStore(baseDir)
        val outside = File(baseDir, "outside.txt").apply { writeText("keep me") }
        val ownedPart = File(store.getTaskTempDir("task-1"), "part-0.tmp").apply { writeText("old part") }
        store.getTaskMetaFile("task-1").writeText(JSONObject().put("schemaVersion", 1).put("segments",
            org.json.JSONArray().put(JSONObject().put("segmentId", "seg-1").put("taskId", "task-1")
                .put("index", 0).put("startByte", 0).put("endByte", 100)
                .put("tmpFilePath", outside.absolutePath))).toString())

        assertTrue(store.readSegments("task-1").isEmpty())
        assertEquals("keep me", outside.readText())
        assertTrue(!ownedPart.exists())
    }

    @Test
    fun `磁盘分片身份或范围损坏时不会作为续传记录返回`() {
        val baseDir = Files.createTempDirectory("download-store-invalid-identity").toFile()
        val store = DownloadStore(baseDir)
        val path = File(store.getTaskTempDir("task-1"), "part-0.tmp").absolutePath
        val records = listOf(
            JSONObject().put("taskId", "another-task").put("startByte", 0).put("endByte", 100),
            JSONObject().put("taskId", "task-1").put("startByte", 1).put("endByte", 100),
            JSONObject().put("taskId", "task-1").put("startByte", 0).put("endByte", Long.MAX_VALUE),
        )
        records.forEach { record ->
            record.put("segmentId", "seg-1").put("index", 0).put("tmpFilePath", path)
            store.getTaskMetaFile("task-1").writeText(JSONObject().put("schemaVersion", 1)
                .put("segments", org.json.JSONArray().put(record)).toString())
            assertTrue(store.readSegments("task-1").isEmpty())
        }
    }

    @Test
    fun `非对象分片记录会安全清理缓存而不是复用孤立分片`() {
        val baseDir = Files.createTempDirectory("download-store-malformed-item").toFile()
        val store = DownloadStore(baseDir)
        val part = File(store.getTaskTempDir("task-1"), "part-0.tmp").apply { writeText("old partial") }
        store.getTaskMetaFile("task-1").writeText(JSONObject().put("schemaVersion", 1)
            .put("segments", org.json.JSONArray().put("invalid item")).toString())
        assertTrue(store.readSegments("task-1").isEmpty())
        assertTrue(!part.exists())
    }

    @Test
    fun `meta 或临时文件符号链接不能改写任务外的文件`() {
        listOf("meta.json", "meta.json.tmp").forEach { fileName ->
            val baseDir = Files.createTempDirectory("download-store-meta-symlink").toFile()
            val store = DownloadStore(baseDir)
            val outside = File(baseDir, "outside.json").apply { writeText("external sentinel") }
            val link = File(store.getTaskTempDir("task-1"), fileName)
            val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()); true }.getOrDefault(false)
            assumeTrue("当前文件系统没有创建符号链接的权限", created)
            assertThrows(IllegalArgumentException::class.java) {
                store.saveMeta("task-1", DownloadRemoteMeta(1024))
            }
            assertThrows(IllegalArgumentException::class.java) { store.readMeta("task-1") }
            assertEquals("external sentinel", outside.readText())
        }
    }

    private companion object {
        const val MAX_TASK_ID_LENGTH = 160
    }
}
