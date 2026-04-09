package com.nio.appstore.core.downloader

import com.nio.appstore.core.storage.VersionedJsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadStore(
    private val baseDir: File,
) {
    fun getTaskTempDir(taskId: String): File {
        val dir = File(baseDir, "temp/$taskId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getFinalDir(): File {
        val dir = File(baseDir, "final")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTaskMetaFile(taskId: String): File = File(getTaskTempDir(taskId), "meta.json")

    private fun metaStore(taskId: String): VersionedJsonStore = VersionedJsonStore(
        storeFile = getTaskMetaFile(taskId),
        schemaVersion = DOWNLOAD_META_SCHEMA_VERSION,
        defaultRootFactory = ::createEmptyMetaRoot,
        migration = ::migrateMetaRoot,
    )

    fun saveMeta(taskId: String, meta: DownloadRemoteMeta) {
        metaStore(taskId).update { root ->
            root.put("contentLength", meta.contentLength)
            root.put("eTag", meta.eTag)
            root.put("lastModified", meta.lastModified)
            root.put("supportsRange", meta.supportsRange)
            root.put("mimeType", meta.mimeType)
        }
    }

    fun readMeta(taskId: String): DownloadRemoteMeta? {
        val file = getTaskMetaFile(taskId)
        if (!file.exists()) return null
        return metaStore(taskId).read { obj ->
            DownloadRemoteMeta(
                contentLength = obj.optLong("contentLength", -1L),
                eTag = obj.optString("eTag").ifBlank { null },
                lastModified = obj.optString("lastModified").ifBlank { null },
                supportsRange = obj.optBoolean("supportsRange", false),
                mimeType = obj.optString("mimeType").ifBlank { null },
            )
        }
    }

    fun saveSegments(taskId: String, segments: List<DownloadSegmentRecord>) {
        metaStore(taskId).update { root ->
            root.put("segments", JSONArray().apply {
                segments.forEach { seg ->
                    put(JSONObject().apply {
                        put("segmentId", seg.segmentId)
                        put("taskId", seg.taskId)
                        put("index", seg.index)
                        put("startByte", seg.startByte)
                        put("endByte", seg.endByte)
                        put("downloadedBytes", seg.downloadedBytes)
                        put("status", seg.status)
                        put("tmpFilePath", seg.tmpFilePath)
                        put("retryCount", seg.retryCount)
                        put("createdAt", seg.createdAt)
                        put("updatedAt", seg.updatedAt)
                    })
                }
            })
        }
    }

    fun readSegments(taskId: String): List<DownloadSegmentRecord> {
        val file = getTaskMetaFile(taskId)
        if (!file.exists()) return emptyList()
        return metaStore(taskId).read { obj ->
            val arr = obj.optJSONArray("segments") ?: JSONArray()
            buildList {
                repeat(arr.length()) { index ->
                    val item = arr.getJSONObject(index)
                    add(
                        DownloadSegmentRecord(
                            segmentId = item.optString("segmentId"),
                            taskId = item.optString("taskId"),
                            index = item.optInt("index"),
                            startByte = item.optLong("startByte"),
                            endByte = item.optLong("endByte"),
                            downloadedBytes = item.optLong("downloadedBytes"),
                            status = item.optString("status"),
                            tmpFilePath = item.optString("tmpFilePath"),
                            retryCount = item.optInt("retryCount", 0),
                            createdAt = item.optLong("createdAt"),
                            updatedAt = item.optLong("updatedAt"),
                        )
                    )
                }
            }
        }
    }

    private fun createEmptyMetaRoot(): JSONObject = JSONObject().apply {
        put("segments", JSONArray())
    }

    private fun migrateMetaRoot(rawValue: Any): JSONObject {
        val legacyRoot = rawValue as? JSONObject ?: return createEmptyMetaRoot()
        return createEmptyMetaRoot().apply {
            put("contentLength", legacyRoot.optLong("contentLength", -1L))
            put("eTag", legacyRoot.optString("eTag").ifBlank { null })
            put("lastModified", legacyRoot.optString("lastModified").ifBlank { null })
            put("supportsRange", legacyRoot.optBoolean("supportsRange", false))
            put("mimeType", legacyRoot.optString("mimeType").ifBlank { null })
            put("segments", JSONArray((legacyRoot.optJSONArray("segments") ?: JSONArray()).toString()))
        }
    }

    private companion object {
        /** 下载 meta 文件当前 schema 版本。 */
        const val DOWNLOAD_META_SCHEMA_VERSION = 1
    }
}
