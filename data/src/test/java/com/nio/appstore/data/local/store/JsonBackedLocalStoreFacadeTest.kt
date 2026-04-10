package com.nio.appstore.data.local.store

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JsonBackedLocalStoreFacadeTest {

    @Test
    fun `读取旧结构化 store 时会补齐 schemaVersion 并保留分片映射`() {
        val storeFile = createTempFile("structured-store.json")
        storeFile.writeText(
            """
            {
              "installedApps": {},
              "downloadTasks": {
                "app.demo": {
                  "taskId": "task-1",
                  "appId": "app.demo",
                  "status": "COMPLETED",
                  "progress": 100,
                  "targetFilePath": "/tmp/app-demo.apk",
                  "downloadedBytes": 2048,
                  "totalBytes": 2048,
                  "createdAt": 1,
                  "updatedAt": 2
                }
              },
              "downloadSegments": {
                "app.demo": [
                  {
                    "segmentId": "seg-1",
                    "taskId": "task-1",
                    "index": 0,
                    "startByte": 0,
                    "endByte": 2047,
                    "downloadedBytes": 2048,
                    "status": "COMPLETED",
                    "tmpFilePath": "/tmp/seg-1.tmp",
                    "createdAt": 1,
                    "updatedAt": 2
                  }
                ]
              },
              "downloadArtifactRefs": {},
              "installSessions": {},
              "settings": {}
            }
            """.trimIndent()
        )
        val facade = JsonBackedLocalStoreFacade(storeFile)

        val task = facade.getDownloadTask("app.demo")
        val segments = facade.getDownloadSegments("app.demo")
        val persisted = JSONObject(storeFile.readText())

        assertNotNull(task)
        assertEquals("task-1", task?.taskId)
        assertEquals(1, segments.size)
        assertEquals("seg-1", segments.first().segmentId)
        assertEquals(1, persisted.optInt("schemaVersion"))
    }

    private fun createTempFile(fileName: String): File {
        return Files.createTempDirectory("structured-store-test").resolve(fileName).toFile()
    }
}
