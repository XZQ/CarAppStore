package com.xzq.appstore.core.storage

import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    /** 当前 store 对应的本地文件。 */
    private val storeFile: File,
    /** 当前 store 期待的 schema 版本。 */
    private val schemaVersion: Int,
    /** 创建空根节点的工厂方法。 */
    private val defaultRootFactory: () -> JSONObject,
    /** 读取到旧格式数据时使用的迁移函数。 */
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
        /** 已标准化为当前 schema 的根节点。 */
        val root: JSONObject,
        /** 是否需要在本次读取后立即回写。 */
        val shouldPersist: Boolean,
    )

    /** 在锁内加载并标准化当前根节点。 */
    private fun loadStateLocked(): LoadState {
        val rawValue = readRawValueLocked() ?: return LoadState(createDefaultRoot(), false)
        val root = when (rawValue) {
            is JSONObject -> rawValue
            else -> migration(rawValue)
        }
        val currentVersion = root.optInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION_UNKNOWN)
        // 版本不一致时统一走迁移逻辑，再补上最新 schemaVersion。
        val normalizedRoot = if (currentVersion == schemaVersion) {
            root
        } else {
            migration(rawValue)
        }.apply {
            put(KEY_SCHEMA_VERSION, schemaVersion)
        }
        return LoadState(normalizedRoot, currentVersion != schemaVersion)
    }

    /** 在锁内读取原始 JSON 值。 */
    private fun readRawValueLocked(): Any? {
        if (!storeFile.exists()) {
            return null
        }
        val rawText = storeFile.readText(StandardCharsets.UTF_8)
        if (rawText.isBlank()) {
            return null
        }
        return runCatching { JSONTokener(rawText).nextValue() }.getOrNull()
    }

    /** 创建带 schemaVersion 的默认根节点。 */
    private fun createDefaultRoot(): JSONObject = defaultRootFactory().apply {
        put(KEY_SCHEMA_VERSION, schemaVersion)
    }

    /**
     * 通过临时文件原子覆盖写入方式持久化根节点。
     *
     * 写流程：写 temp → fsync（保证内容落盘）→ Files.move(ATOMIC_MOVE) → fsync 父目录。
     * 这样即使中途断电，正式文件要么是上一次完整内容，要么是新内容，不会出现半写状态。
     */
    private fun persistLocked(root: JSONObject) {
        storeFile.parentFile?.mkdirs()
        val normalizedRoot = JSONObject(root.toString()).apply {
            put(KEY_SCHEMA_VERSION, schemaVersion)
        }
        val tempFile = File(storeFile.parentFile, storeFile.name + TEMP_FILE_SUFFIX)
        // 显式用 FileOutputStream + fd.sync 实现 fsync，避免 page cache 在断电时丢失。
        FileOutputStream(tempFile).use { fos ->
            fos.write(normalizedRoot.toString().toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        // 优先走 Files.move(ATOMIC_MOVE)；失败再回退到 copyTo+delete。
        val moved = runCatching {
            Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrElse { false }
        if (!moved) {
            tempFile.copyTo(storeFile, overwrite = true)
            tempFile.delete()
        }
        // 父目录 fsync，确保目录项已落盘，避免 rename 在断电后被回滚。
        runCatching {
            FileOutputStream(storeFile.parentFile).use { fos -> fos.fd.sync() }
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
