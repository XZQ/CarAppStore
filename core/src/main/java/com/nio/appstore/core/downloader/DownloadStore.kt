package com.nio.appstore.core.downloader
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

    fun saveMeta(taskId: String, meta: DownloadRemoteMeta) {
        val json = JSONObject().apply {
            put("contentLength", meta.contentLength)
            put("eTag", meta.eTag)
            put("lastModified", meta.lastModified)
            put("supportsRange", meta.supportsRange)
            put("mimeType", meta.mimeType)
        }
        getTaskMetaFile(taskId).writeText(json.toString())
    }

    fun readMeta(taskId: String): DownloadRemoteMeta? {
        val file = getTaskMetaFile(taskId)
        if (!file.exists()) return null
        val obj = JSONObject(file.readText())
        return DownloadRemoteMeta(
            contentLength = obj.optLong("contentLength", -1L),
            eTag = obj.optString("eTag").ifBlank { null },
            lastModified = obj.optString("lastModified").ifBlank { null },
            supportsRange = obj.optBoolean("supportsRange", false),
            mimeType = obj.optString("mimeType").ifBlank { null },
        )
    }

    fun saveSegments(taskId: String, segments: List<DownloadSegmentRecord>) {
        val obj = JSONObject(readMeta(taskId)?.let {
            JSONObject().apply {
                put("contentLength", it.contentLength)
                put("eTag", it.eTag)
                put("lastModified", it.lastModified)
                put("supportsRange", it.supportsRange)
                put("mimeType", it.mimeType)
            }
        }?.toString() ?: "{}")
        obj.put("segments", JSONArray().apply {
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
        getTaskMetaFile(taskId).writeText(obj.toString())
    }

    fun readSegments(taskId: String): List<DownloadSegmentRecord> {
        val file = getTaskMetaFile(taskId)
        if (!file.exists()) return emptyList()
        val obj = JSONObject(file.readText())
        val arr = obj.optJSONArray("segments") ?: JSONArray()
        return buildList {
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
