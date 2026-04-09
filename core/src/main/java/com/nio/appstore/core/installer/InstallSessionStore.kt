package com.nio.appstore.core.installer

import com.nio.appstore.core.storage.VersionedJsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class InstallSessionStore(
    private val storeFile: File,
) {
    /** 安装会话本地存储的统一版本化入口。 */
    private val jsonStore = VersionedJsonStore(
        storeFile = storeFile,
        schemaVersion = INSTALL_SESSION_SCHEMA_VERSION,
        defaultRootFactory = ::createEmptyRoot,
        migration = ::migrateRoot,
    )

    fun save(record: InstallSessionRecord) {
        jsonStore.update { root ->
            val current = readRecords(root).associateBy { it.sessionId }.toMutableMap()
            current[record.sessionId] = record
            root.put(KEY_SESSIONS, recordsToJsonArray(current.values.sortedByDescending { it.updatedAt }))
        }
    }

    fun get(sessionId: Int): InstallSessionRecord? {
        return readAllFromJson().firstOrNull { it.sessionId == sessionId }
    }

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

    fun getLatestByAppId(appId: String): InstallSessionRecord? {
        return readAll().filter { it.appId == appId }.maxByOrNull { it.updatedAt }
    }

    fun getRecoverableSessions(): List<InstallSessionRecord> {
        return readAll().filter { record ->
            InstallSessionStatus.isRecoverable(record.status)
        }
    }

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

    fun getRetryableSessions(): List<InstallSessionRecord> {
        return readAll().filter {
            InstallSessionStatus.isRetryable(it.status)
        }
    }

    fun clearFailedSessions(): Int {
        return jsonStore.update { root ->
            val current = readRecords(root)
            val keep = current.filterNot { InstallSessionStatus.isRetryable(it.status) }
            val removed = current.size - keep.size
            if (removed > 0) {
                root.put(KEY_SESSIONS, recordsToJsonArray(keep.sortedByDescending { it.updatedAt }))
            }
            removed
        }
    }

    fun readAll(): List<InstallSessionRecord> {
        return readAllFromJson()
    }

    private fun readAllFromJson(): List<InstallSessionRecord> = jsonStore.read { root ->
        readRecords(root)
    }

    private fun createEmptyRoot(): JSONObject = JSONObject().apply {
        put(KEY_SESSIONS, JSONArray())
    }

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
