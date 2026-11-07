package com.xzq.appstore.core.storage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier

class VersionedJsonStoreTest {

    @Test
    fun `read 会迁移旧格式并回写 schemaVersion`() {
        val storeFile = createTempFile("versioned-json-store.json")
        storeFile.writeText("""{"payload":"legacy"}""")
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 2,
            defaultRootFactory = { JSONObject().put("payload", "default") },
            migration = { rawValue ->
                val legacyRoot = rawValue as JSONObject
                JSONObject().apply {
                    put("payload", legacyRoot.optString("payload"))
                    put("migrated", true)
                }
            },
        )

        val root = store.read { it }
        val persisted = JSONObject(storeFile.readText())

        assertEquals("legacy", root.optString("payload"))
        assertTrue(root.optBoolean("migrated"))
        assertEquals(2, persisted.optInt("schemaVersion"))
        assertTrue(persisted.optBoolean("migrated"))
    }

    @Test
    fun `update 会基于默认根节点写入内容`() {
        val storeFile = createTempFile("versioned-json-update.json")
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 1,
            defaultRootFactory = { JSONObject().put("counter", 0) },
            migration = { JSONObject() },
        )

        store.update { root ->
            root.put("counter", root.optInt("counter") + 1)
        }

        val persisted = JSONObject(storeFile.readText())
        assertEquals(1, persisted.optInt("counter"))
        assertEquals(1, persisted.optInt("schemaVersion"))
    }

    @Test
    fun `并发 update 不会丢失写入`() {
        val storeFile = createTempFile("versioned-json-concurrent.json")
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 1,
            defaultRootFactory = { JSONObject().put("counter", 0) },
            migration = { JSONObject() },
        )
        val threadCount = 20
        val barrier = CyclicBarrier(threadCount)
        val threads = (0 until threadCount).map { idx ->
            Thread {
                barrier.await()
                store.update { root ->
                    root.put("counter", root.optInt("counter") + 1)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000L) }

        val finalValue = store.read { it.optInt("counter") }
        assertEquals(threadCount, finalValue)
    }

    @Test
    fun `同一文件的多个 store 实例并发 update 不会丢失写入`() {
        val storeFile = createTempFile("versioned-json-cross-instance.json")
        val stores = List(2) {
            VersionedJsonStore(
                storeFile = storeFile,
                schemaVersion = 1,
                defaultRootFactory = { JSONObject().put("counter", 0) },
                migration = { JSONObject() },
            )
        }
        val threadCount = 20
        val barrier = CyclicBarrier(threadCount)
        val threads = (0 until threadCount).map { index ->
            Thread {
                barrier.await()
                stores[index % stores.size].update { root ->
                    root.put("counter", root.optInt("counter") + 1)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000L) }

        assertEquals(threadCount, stores.first().read { it.optInt("counter") })
    }

    @Test
    fun `空文件被视为不存在并使用默认值`() {
        val storeFile = createTempFile("versioned-json-empty.json")
        storeFile.writeText("")
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 1,
            defaultRootFactory = { JSONObject().put("value", "default") },
            migration = { JSONObject() },
        )
        val result = store.read { it.optString("value") }
        assertEquals("default", result)
    }

    @Test
    fun `损坏的 JSON 文件会被隔离备份且读取回退到默认值`() {
        val storeFile = createTempFile("versioned-json-broken.json")
        val corruptedContent = """{"payload":"half-written"""
        storeFile.writeText(corruptedContent)
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 1,
            defaultRootFactory = { JSONObject().put("value", "default") },
            migration = { JSONObject() },
        )

        val result = store.read { it.optString("value") }

        assertEquals("default", result)
        val backupFile = File(storeFile.parentFile, storeFile.name + ".corrupted")
        assertTrue("corrupted file must be quarantined as a sidecar backup", backupFile.isFile)
        assertEquals(corruptedContent, backupFile.readText())
        // 损坏现场被移走后，read 不得把默认空根回写到原文件覆盖现场。
        assertTrue("original file must not be rewritten by a plain read", !storeFile.exists())
    }

    @Test
    fun `损坏文件后的 update 写入新数据且保留备份`() {
        val storeFile = createTempFile("versioned-json-broken-update.json")
        storeFile.writeText("""{"counter":7,"truncated":""")
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 1,
            defaultRootFactory = { JSONObject().put("counter", 0) },
            migration = { JSONObject() },
        )

        store.update { root ->
            root.put("counter", root.optInt("counter") + 1)
        }

        val persisted = JSONObject(storeFile.readText())
        assertEquals(1, persisted.optInt("counter"))
        assertEquals(1, persisted.optInt("schemaVersion"))
        val backupFile = File(storeFile.parentFile, storeFile.name + ".corrupted")
        assertTrue(backupFile.isFile)
        assertTrue(backupFile.readText().contains("truncated"))
    }

    @Test
    fun `连续两次损坏只保留一代备份`() {
        val storeFile = createTempFile("versioned-json-broken-twice.json")
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 1,
            defaultRootFactory = { JSONObject().put("value", "default") },
            migration = { JSONObject() },
        )
        storeFile.writeText("""{"first":""")
        store.read { it }
        storeFile.writeText("""{"second":""")
        val result = store.read { it.optString("value") }

        assertEquals("default", result)
        val backupFile = File(storeFile.parentFile, storeFile.name + ".corrupted")
        assertTrue(backupFile.isFile)
        assertTrue(backupFile.readText().contains("second"))
    }

    @Test
    fun `多次 update 保持 schemaVersion 不变`() {
        val storeFile = createTempFile("versioned-json-multi.json")
        val store = VersionedJsonStore(
            storeFile = storeFile,
            schemaVersion = 3,
            defaultRootFactory = { JSONObject() },
            migration = { JSONObject() },
        )
        repeat(5) {
            store.update { root -> root.put("step", it) }
        }
        val persisted = JSONObject(storeFile.readText())
        assertEquals(3, persisted.optInt("schemaVersion"))
        assertEquals(4, persisted.optInt("step"))
    }

    private fun createTempFile(fileName: String): File {
        return Files.createTempDirectory("versioned-json-store-test").resolve(fileName).toFile()
    }
}
