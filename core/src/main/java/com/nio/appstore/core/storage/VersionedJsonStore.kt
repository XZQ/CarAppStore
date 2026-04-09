package com.nio.appstore.core.storage

import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * VersionedJsonStore 负责统一管理带版本号的 JSON 文件读写。
 *
 * 目标包括：
 * 1. 为本地 JSON 数据增加 schemaVersion；
 * 2. 在读取旧格式时执行迁移；
 * 3. 在同一进程内为读写提供并发保护；
 * 4. 通过临时文件覆盖写入，尽量降低中途写坏文件的风险。
 */
class VersionedJsonStore(
    private val storeFile: File,
    private val schemaVersion: Int,
    private val defaultRootFactory: () -> JSONObject,
    private val migration: (Any) -> JSONObject,
) {

    /** 保护同一 JSON 文件在进程内的并发读写。 */
    private val fileLock = ReentrantLock()

    /**
     * 读取当前根节点。
     *
     * 如果发现旧 schema，会先完成迁移并自动回写。
     */
    fun <T> read(block: (JSONObject) -> T): T = fileLock.withLock {
        val state = loadStateLocked()
        if (state.shouldPersist) {
            persistLocked(state.root)
        }
        block(JSONObject(state.root.toString()))
    }

    /**
     * 在同一把锁内读取、修改并回写根节点。
     */
    fun <T> update(block: (JSONObject) -> T): T = fileLock.withLock {
        val state = loadStateLocked()
        val result = block(state.root)
        persistLocked(state.root)
        result
    }

    /** 描述一次加载后的根节点与是否需要回写。 */
    private class LoadState(
        val root: JSONObject,
        val shouldPersist: Boolean,
    )

    private fun loadStateLocked(): LoadState {
        val rawValue = readRawValueLocked() ?: return LoadState(createDefaultRoot(), false)
        val root = when (rawValue) {
            is JSONObject -> rawValue
            else -> migration(rawValue)
        }
        val currentVersion = root.optInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION_UNKNOWN)
        val normalizedRoot = if (currentVersion == schemaVersion) {
            root
        } else {
            migration(rawValue)
        }.apply {
            put(KEY_SCHEMA_VERSION, schemaVersion)
        }
        return LoadState(normalizedRoot, currentVersion != schemaVersion)
    }

    private fun readRawValueLocked(): Any? {
        if (!storeFile.exists()) return null
        val rawText = storeFile.readText(StandardCharsets.UTF_8)
        if (rawText.isBlank()) return null
        return runCatching { JSONTokener(rawText).nextValue() }.getOrNull()
    }

    private fun createDefaultRoot(): JSONObject = defaultRootFactory().apply {
        put(KEY_SCHEMA_VERSION, schemaVersion)
    }

    private fun persistLocked(root: JSONObject) {
        storeFile.parentFile?.mkdirs()
        val normalizedRoot = JSONObject(root.toString()).apply {
            put(KEY_SCHEMA_VERSION, schemaVersion)
        }
        val tempFile = File(storeFile.parentFile, storeFile.name + TEMP_FILE_SUFFIX)
        tempFile.writeText(normalizedRoot.toString(), StandardCharsets.UTF_8)
        if (!tempFile.renameTo(storeFile)) {
            tempFile.copyTo(storeFile, overwrite = true)
            tempFile.delete()
        }
    }

    private companion object {
        /** schemaVersion 不存在时使用的默认值。 */
        const val SCHEMA_VERSION_UNKNOWN = -1

        /** 根节点统一使用的 schemaVersion 字段名。 */
        const val KEY_SCHEMA_VERSION = "schemaVersion"

        /** 临时文件后缀。 */
        const val TEMP_FILE_SUFFIX = ".tmp"
    }
}
