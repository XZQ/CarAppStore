package com.nio.appstore.core.installer

import com.nio.appstore.core.storage.VersionedJsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class InstallSessionStore(
    /** 安装会话 JSON 文件路径。 */
    private val storeFile: File,
) {
    /** 安装会话本地存储的统一版本化入口。 */
    private val jsonStore = VersionedJsonStore(
        storeFile = storeFile,
        schemaVersion = INSTALL_SESSION_SCHEMA_VERSION,
        defaultRootFactory = ::createEmptyRoot,
        migration = ::migrateRoot,
    )

    /** 保存或覆盖一个安装会话记录。 */
    fun save(record: InstallSessionRecord) {
        jsonStore.update { root ->
            // 先按 sessionId 建索引，再写回，避免同一会话出现重复记录。
            val current = readRecords(root).associateBy { it.sessionId }.toMutableMap()
            current[record.sessionId] = record
            root.put(KEY_SESSIONS, recordsToJsonArray(current.values.sortedByDescending { it.updatedAt }))
        }
    }

    /** 按 sessionId 读取安装会话。 */
    fun get(sessionId: Int): InstallSessionRecord? {
        return readAllFromJson().firstOrNull { it.sessionId == sessionId }
    }

    /** 更新指定安装会话的状态和失败信息。 */
    fun updateStatus(
        sessionId: Int,
        status: String,
        progress: Int,
        failureCode: String? = null,
        failureMessage: String? = null,
    ) {
        val current = get(sessionId) ?: return
        save(
            current.copy(
                status = status,
                progress = progress,
                failureCode = failureCode,
                failureMessage = failureMessage,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** 获取指定应用最近一次安装会话。 */
    fun getLatestByAppId(appId: String): InstallSessionRecord? {
        return readAll().filter { it.appId == appId }.maxByOrNull { it.updatedAt }
    }

    /** 获取启动恢复时需要处理的安装会话。 */
    fun getRecoverableSessions(): List<InstallSessionRecord> {
        return readAll().filter { record ->
            InstallSessionStatus.isRecoverable(record.status)
        }
    }

    /** 将上次异常中断的可恢复会话标记为“恢复后中断”。 */
    fun markRecoveredSessionsAsInterrupted() {
        val recoverable = getRecoverableSessions()
        recoverable.forEach { record ->
            save(
                record.copy(
                    status = InstallSessionStatus.RECOVERED_INTERRUPTED,
                    failureCode = record.failureCode ?: InstallFailureCode.INSTALL_INTERRUPTED.name,
                    failureMessage = record.failureMessage ?: InstallerText.SESSION_INTERRUPTED_RECOVERABLE,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** 获取允许用户一键重试的安装会话。 */
    fun getRetryableSessions(): List<InstallSessionRecord> {
        return readAll().filter {
            InstallSessionStatus.isRetryable(it.status)
        }
    }

    /** 清理失败/可重试会话，并返回删除数量。 */
    fun clearFailedSessions(): Int {
        return jsonStore.update { root ->
            val current = readRecords(root)
            val keep = current.filterNot { InstallSessionStatus.isRetryable(it.status) }
            val removed = current.size - keep.size
            if (removed > 0) {
                // 仅在确实有删除时回写，避免无意义改动文件时间戳。
                root.put(KEY_SESSIONS, recordsToJsonArray(keep.sortedByDescending { it.updatedAt }))
            }
            removed
        }
    }

    /** 读取全部安装会话记录。 */
    fun readAll(): List<InstallSessionRecord> {
        return readAllFromJson()
    }

    /** 从版本化 JSON store 中读取全部记录。 */
    private fun readAllFromJson(): List<InstallSessionRecord> = jsonStore.read { root ->
        readRecords(root)
    }

    /** 创建空的安装会话根节点。 */
    private fun createEmptyRoot(): JSONObject = JSONObject().apply {
        put(KEY_SESSIONS, JSONArray())
    }

    /** 将旧格式安装会话结构迁移为当前对象结构。 */
    private fun migrateRoot(rawValue: Any): JSONObject {
        return when (rawValue) {
            is JSONArray -> createEmptyRoot().apply { put(KEY_SESSIONS, JSONArray(rawValue.toString())) }
            is JSONObject -> createEmptyRoot().apply {
                val sessions = rawValue.optJSONArray(KEY_SESSIONS) ?: JSONArray()
                put(KEY_SESSIONS, JSONArray(sessions.toString()))
            }
            else -> createEmptyRoot()
        }
    }

    /** 从 JSON 根节点解析安装会话列表。 */
    private fun readRecords(root: JSONObject): List<InstallSessionRecord> {
        val arr = root.optJSONArray(KEY_SESSIONS) ?: JSONArray()
        return buildList {
            repeat(arr.length()) { index ->
                val item = arr.getJSONObject(index)
                add(
                    InstallSessionRecord(
                        sessionId = item.optInt("sessionId", -1),
                        appId = item.optString("appId"),
                        packageName = item.optString("packageName"),
                        apkPath = item.optString("apkPath"),
                        targetVersion = item.optString("targetVersion"),
                        status = item.optString("status"),
                        progress = item.optInt("progress", 0),
                        failureCode = item.optString("failureCode").ifBlank { null },
                        failureMessage = item.optString("failureMessage").ifBlank { null },
                        createdAt = item.optLong("createdAt"),
                        updatedAt = item.optLong("updatedAt"),
                    )
                )
            }
        }
    }

    /** 将安装会话记录列表编码为 JSON 数组。 */
    private fun recordsToJsonArray(records: Collection<InstallSessionRecord>): JSONArray {
        val arr = JSONArray()
        records.forEach { rec ->
            arr.put(JSONObject().apply {
                put("sessionId", rec.sessionId)
                put("appId", rec.appId)
                put("packageName", rec.packageName)
                put("apkPath", rec.apkPath)
                put("targetVersion", rec.targetVersion)
                put("status", rec.status)
                put("progress", rec.progress)
                put("failureCode", rec.failureCode)
                put("failureMessage", rec.failureMessage)
                put("createdAt", rec.createdAt)
                put("updatedAt", rec.updatedAt)
            })
        }
        return arr
    }

    private companion object {
        /** 安装会话 store 当前 schema 版本。 */
        const val INSTALL_SESSION_SCHEMA_VERSION = 1

        /** 安装会话数组字段名。 */
        const val KEY_SESSIONS = "sessions"
    }
}
