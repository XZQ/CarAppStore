package com.nio.appstore.core.installer

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class InstallSessionStore(
    private val storeFile: File,
) {
    fun save(record: InstallSessionRecord) {
        val current = readAllFromJson().associateBy { it.sessionId }.toMutableMap()
        current[record.sessionId] = record
        persistJson(current.values.sortedByDescending { it.updatedAt })
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
        val current = readAll()
        val keep = current.filterNot { InstallSessionStatus.isRetryable(it.status) }
        val removed = current.size - keep.size
        if (removed > 0) {
            persistJson(keep.sortedByDescending { it.updatedAt })
        }
        return removed
    }

    fun readAll(): List<InstallSessionRecord> {
        return readAllFromJson()
    }

    private fun readAllFromJson(): List<InstallSessionRecord> {
        if (!storeFile.exists()) return emptyList()
        val text = storeFile.readText()
        if (text.isBlank()) return emptyList()
        val arr = JSONArray(text)
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

    private fun persistJson(records: List<InstallSessionRecord>) {
        storeFile.parentFile?.mkdirs()
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
        storeFile.writeText(arr.toString())
    }
}
