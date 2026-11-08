package com.xzq.appstore.core.storage

import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
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
    /** 保护同一 JSON 文件在进程内跨 Store 实例的并发读写。 */
    private val fileLock = sharedFileLock(storeFile)

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
        // 降级写回前先留一份原始文件：旧版本无法理解的字段一旦被覆盖就永久丢失，
        // 备份让重新安装新版本时仍有机会人工恢复。
        if (state.fromNewerSchema) {
            backupNewerSchemaFileLocked()
        }
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
        /** 磁盘上的 schema 是否比当前程序更新（即发生了版本回退）。 */
        val fromNewerSchema: Boolean = false,
    )

    /** 在锁内加载并标准化当前根节点。 */
    private fun loadStateLocked(): LoadState {
        val rawValue = readRawValueLocked() ?: return LoadState(createDefaultRoot(), false)
        val root = when (rawValue) {
            is JSONObject -> rawValue
            else -> migration(rawValue)
        }
        val currentVersion = root.optInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION_UNKNOWN)
        if (currentVersion > schemaVersion) {
            // 用户回退安装旧版本时会读到更高 schema 的文件。此时不能按「旧格式升级」处理：
            // 迁移函数不了解新字段，回写会把它们连同 schemaVersion 一起永久降级。
            // 本次读取按原样返回，且不触发任何自动回写。
            return LoadState(root, shouldPersist = false, fromNewerSchema = true)
        }
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
        return try {
            JSONTokener(rawText).nextValue()
        } catch (failure: Exception) {
            // 解析失败说明文件已损坏（半写、断电或外部篡改）：先把损坏现场隔离备份，
            // 再按空数据处理，避免后续 update 把默认空根直接覆盖掉唯一可诊断的现场。
            quarantineCorruptedFileLocked()
            null
        }
    }

    /**
     * 把损坏的 JSON 文件改名为侧车备份，最多保留一代。
     *
     * 备份失败不阻断读取：原文件内容本身已不可用，后续原子写会替换它。
     */
    private fun quarantineCorruptedFileLocked() {
        val backupFile = File(storeFile.parentFile, storeFile.name + CORRUPTED_FILE_SUFFIX)
        val moved = runCatching {
            Files.move(storeFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrDefault(false)
        if (!moved) {
            // 跨设备 rename 或文件被占用时，至少先复制现场再移除主文件，
            // 避免主文件持续损坏导致每次读取都重复隔离，或被后续写入覆盖掉唯一现场。
            runCatching {
                storeFile.copyTo(backupFile, overwrite = true)
                storeFile.delete()
            }
        }
    }

    /**
     * 把更新 schema 的原始文件复制为侧车备份，只在首次降级写入前执行一次。
     *
     * 备份失败不阻断写入：此时继续保留主文件反而会让每次启动都重复尝试备份。
     */
    private fun backupNewerSchemaFileLocked() {
        val backupFile = File(storeFile.parentFile, storeFile.name + NEWER_SCHEMA_FILE_SUFFIX)
        if (backupFile.exists()) {
            return
        }
        runCatching { storeFile.copyTo(backupFile, overwrite = false) }
    }

    /** 创建带 schemaVersion 的默认根节点。 */
    private fun createDefaultRoot(): JSONObject = defaultRootFactory().apply {
        put(KEY_SCHEMA_VERSION, schemaVersion)
    }

    /**
     * 通过临时文件原子覆盖写入方式持久化根节点。
     *
     * 写流程：写 temp → fsync（保证内容落盘）→ 同目录 rename → 尽力 fsync 父目录。
     * 临时文件与目标文件同目录，rename 在同一文件系统内完成；即使中途断电，
     * 正式文件要么是上一次完整内容，要么是新内容，不会出现半写状态。
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
        // 优先走 Files.move(ATOMIC_MOVE)；部分文件系统不支持原子替换时回退到同目录 rename，
        // POSIX rename / Windows MoveFileEx 在同卷内依然原子，绝不退化为 copy+delete。
        val moved = runCatching {
            Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrElse { false }
        if (!moved) {
            Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        // 父目录 fsync，确保目录项已落盘，避免 rename 在断电后被回滚。
        // FileOutputStream 打开目录在 Android/Linux 会抛 EISDIR，必须用只读 FileChannel；
        // Windows 不支持目录 fsync，失败时保持尽力而为。
        runCatching {
            FileChannel.open(storeFile.parentFile.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        }
    }

    private companion object {
        /** 按规范化文件路径复用进程级锁，避免多个 Store 实例分别加锁后丢失更新。 */
        val PROCESS_FILE_LOCKS = ConcurrentHashMap<String, ReentrantLock>()

        /** schemaVersion 不存在时使用的默认值。 */
        const val SCHEMA_VERSION_UNKNOWN = -1

        /** 根节点统一使用的 schemaVersion 字段名。 */
        const val KEY_SCHEMA_VERSION = "schemaVersion"

        /** 临时文件后缀。 */
        const val TEMP_FILE_SUFFIX = ".tmp"

        /** 损坏 JSON 现场备份的文件后缀，最多保留一代。 */
        const val CORRUPTED_FILE_SUFFIX = ".corrupted"

        /** 版本回退（读到更高 schema）时保留的原始文件后缀，最多保留一代。 */
        const val NEWER_SCHEMA_FILE_SUFFIX = ".newer-schema"

        /** 返回指定文件在当前进程内共享的读写锁。 */
        fun sharedFileLock(storeFile: File): ReentrantLock {
            val fileKey = runCatching { storeFile.canonicalPath }.getOrElse { storeFile.absolutePath }
            return PROCESS_FILE_LOCKS.computeIfAbsent(fileKey) { ReentrantLock() }
        }
    }
}
