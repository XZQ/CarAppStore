package com.nio.appstore.core.storage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

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

    private fun createTempFile(fileName: String): File {
        return Files.createTempDirectory("versioned-json-store-test").resolve(fileName).toFile()
    }
}
