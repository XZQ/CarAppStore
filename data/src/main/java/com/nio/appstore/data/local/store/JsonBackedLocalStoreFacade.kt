package com.nio.appstore.data.local.store

import com.nio.appstore.data.local.entity.DownloadArtifactRefEntity
import com.nio.appstore.data.local.entity.DownloadSegmentEntity
import com.nio.appstore.data.local.entity.DownloadTaskEntity
import com.nio.appstore.data.local.entity.InstallSessionEntity
import com.nio.appstore.data.local.entity.InstalledAppEntity
import com.nio.appstore.data.local.entity.SettingsEntity
import com.nio.appstore.core.storage.VersionedJsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonBackedLocalStoreFacade(
    private val storeFile: File,
) : LocalStoreFacade {

    /** 结构化本地数据文件的统一版本化存储入口。 */
    private val jsonStore = VersionedJsonStore(
        storeFile = storeFile,
        schemaVersion = STRUCTURED_STORE_SCHEMA_VERSION,
        defaultRootFactory = ::createEmptyRoot,
        migration = ::migrateRoot,
    )

    override fun saveInstalledApp(entity: InstalledAppEntity) {
        updateStore { root ->
            root.putObjectInMap("installedApps", entity.appId, JSONObject().apply {
                put("appId", entity.appId)
                put("packageName", entity.packageName)
                put("name", entity.name)
                put("versionName", entity.versionName)
            })
        }
    }

    override fun getInstalledApps(): List<InstalledAppEntity> {
        return readMap("installedApps").map { (_, value) ->
            InstalledAppEntity(
                appId = value.optString("appId"),
                packageName = value.optString("packageName"),
                name = value.optString("name"),
                versionName = value.optString("versionName"),
            )
        }
    }

    override fun saveDownloadTask(entity: DownloadTaskEntity) {
        updateStore { root ->
            root.putObjectInMap("downloadTasks", entity.appId, JSONObject().apply {
                put("taskId", entity.taskId)
                put("appId", entity.appId)
                put("status", entity.status)
                put("progress", entity.progress)
                put("targetFilePath", entity.targetFilePath)
                put("downloadedBytes", entity.downloadedBytes)
                put("totalBytes", entity.totalBytes)
                put("speedBytesPerSec", entity.speedBytesPerSec)
                put("failureCode", entity.failureCode)
                put("failureMessage", entity.failureMessage)
                put("retryCount", entity.retryCount)
                put("downloadUrl", entity.downloadUrl)
                put("tempDirPath", entity.tempDirPath)
                put("eTag", entity.eTag)
                put("lastModified", entity.lastModified)
                put("supportsRange", entity.supportsRange)
                put("checksumType", entity.checksumType)
                put("checksumValue", entity.checksumValue)
                put("segmentCount", entity.segmentCount)
                put("createdAt", entity.createdAt)
                put("updatedAt", entity.updatedAt)
            })
        }
    }

    override fun getDownloadTask(appId: String): DownloadTaskEntity? {
        val value = readMap("downloadTasks")[appId] ?: return null
        return value.toDownloadTaskEntity()
    }

    override fun getAllDownloadTasks(): List<DownloadTaskEntity> {
        return readMap("downloadTasks").values
            .map { it.toDownloadTaskEntity() }
            .sortedByDescending { it.updatedAt }
    }

    override fun removeDownloadTask(appId: String) {
        updateStore { root ->
            root.removeObjectInMap("downloadTasks", appId)
            root.removeObjectInMap("downloadSegments", appId)
        }
    }

    override fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentEntity>) {
        updateStore { root ->
            val arr = JSONArray()
            segments.forEach { seg ->
                arr.put(JSONObject().apply {
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
            root.putArrayInMap("downloadSegments", appId, arr)
        }
    }

    override fun getDownloadSegments(appId: String): List<DownloadSegmentEntity> {
        val arr = readArrayMap("downloadSegments")[appId] ?: return emptyList()
        return buildList {
            repeat(arr.length()) { i ->
                val item = arr.getJSONObject(i)
                add(
                    DownloadSegmentEntity(
                        segmentId = item.optString("segmentId"),
                        taskId = item.optString("taskId"),
                        index = item.optInt("index"),
                        startByte = item.optLong("startByte"),
                        endByte = item.optLong("endByte"),
                        downloadedBytes = item.optLong("downloadedBytes"),
                        status = item.optString("status"),
                        tmpFilePath = item.optString("tmpFilePath"),
                        retryCount = item.optInt("retryCount"),
                        createdAt = item.optLong("createdAt"),
                        updatedAt = item.optLong("updatedAt"),
                    )
                )
            }
        }
    }

    override fun saveDownloadArtifactRef(entity: DownloadArtifactRefEntity) {
        updateStore { root ->
            root.putObjectInMap("downloadArtifactRefs", entity.appId, JSONObject().apply {
                put("appId", entity.appId)
                put("apkPath", entity.apkPath)
                put("fileSize", entity.fileSize)
                put("fileExists", entity.fileExists)
                put("updatedAt", entity.updatedAt)
            })
        }
    }

    override fun getDownloadArtifactRef(appId: String): DownloadArtifactRefEntity? {
        val value = readMap("downloadArtifactRefs")[appId] ?: return null
        return DownloadArtifactRefEntity(
            appId = value.optString("appId"),
            apkPath = value.optString("apkPath"),
            fileSize = value.optLong("fileSize"),
            fileExists = value.optBoolean("fileExists"),
            updatedAt = value.optLong("updatedAt"),
        )
    }

    override fun removeDownloadArtifactRef(appId: String) {
        updateStore { root -> root.removeObjectInMap("downloadArtifactRefs", appId) }
    }

    override fun saveInstallSession(entity: InstallSessionEntity) {
        updateStore { root ->
            root.putObjectInMap("installSessions", entity.sessionId.toString(), JSONObject().apply {
                put("sessionId", entity.sessionId)
                put("appId", entity.appId)
                put("packageName", entity.packageName)
                put("apkPath", entity.apkPath)
                put("targetVersion", entity.targetVersion)
                put("status", entity.status)
                put("progress", entity.progress)
                put("failureCode", entity.failureCode)
                put("failureMessage", entity.failureMessage)
                put("createdAt", entity.createdAt)
                put("updatedAt", entity.updatedAt)
            })
        }
    }

    override fun getInstallSessions(): List<InstallSessionEntity> {
        return readMap("installSessions").values
            .map { value ->
                InstallSessionEntity(
                    sessionId = value.optInt("sessionId"),
                    appId = value.optString("appId"),
                    packageName = value.optString("packageName"),
                    apkPath = value.optString("apkPath"),
                    targetVersion = value.optString("targetVersion"),
                    status = value.optString("status"),
                    progress = value.optInt("progress"),
                    failureCode = value.optString("failureCode").ifBlank { null },
                    failureMessage = value.optString("failureMessage").ifBlank { null },
                    createdAt = value.optLong("createdAt"),
                    updatedAt = value.optLong("updatedAt"),
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    override fun saveSetting(entity: SettingsEntity) {
        updateStore { root ->
            root.putObjectInMap("settings", entity.key, JSONObject().apply {
                put("key", entity.key)
                put("value", entity.value)
                put("updatedAt", entity.updatedAt)
            })
        }
    }

    override fun getSetting(key: String): SettingsEntity? {
        val value = readMap("settings")[key] ?: return null
        return SettingsEntity(
            key = value.optString("key"),
            value = value.optString("value"),
            updatedAt = value.optLong("updatedAt"),
        )
    }

    override fun removeSetting(key: String) {
        updateStore { root -> root.removeObjectInMap("settings", key) }
    }

    override fun getAllSettings(): List<SettingsEntity> {
        return readMap("settings").values
            .map { value ->
                SettingsEntity(
                    key = value.optString("key"),
                    value = value.optString("value"),
                    updatedAt = value.optLong("updatedAt"),
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    private fun readRoot(): JSONObject = jsonStore.read { it }

    private fun createEmptyRoot(): JSONObject = JSONObject().apply {
        put("installedApps", JSONObject())
        put("downloadTasks", JSONObject())
        put("downloadSegments", JSONObject())
        put("downloadArtifactRefs", JSONObject())
        put("installSessions", JSONObject())
        put("settings", JSONObject())
    }

    private fun updateStore(block: (JSONObject) -> Unit) {
        jsonStore.update { root -> block(root) }
    }

    private fun migrateRoot(rawValue: Any): JSONObject {
        val legacyRoot = rawValue as? JSONObject ?: return createEmptyRoot()
        return createEmptyRoot().apply {
            copyObjectSectionFrom(legacyRoot, "installedApps")
            copyObjectSectionFrom(legacyRoot, "downloadTasks")
            copyObjectSectionFrom(legacyRoot, "downloadSegments")
            copyObjectSectionFrom(legacyRoot, "downloadArtifactRefs")
            copyObjectSectionFrom(legacyRoot, "installSessions")
            copyObjectSectionFrom(legacyRoot, "settings")
        }
    }

    private fun readMap(name: String): Map<String, JSONObject> {
        val root = readRoot()
        val obj = root.optJSONObject(name) ?: JSONObject()
        val result = linkedMapOf<String, JSONObject>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = obj.optJSONObject(key) ?: JSONObject()
        }
        return result
    }

    private fun readArrayMap(name: String): Map<String, JSONArray> {
        val root = readRoot()
        val obj = root.optJSONObject(name) ?: JSONObject()
        val result = linkedMapOf<String, JSONArray>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = obj.optJSONArray(key) ?: JSONArray()
        }
        return result
    }

    private fun JSONObject.putObjectInMap(mapName: String, key: String, value: JSONObject) {
        val obj = optJSONObject(mapName) ?: JSONObject().also { put(mapName, it) }
        obj.put(key, value)
    }

    private fun JSONObject.putArrayInMap(mapName: String, key: String, value: JSONArray) {
        val obj = optJSONObject(mapName) ?: JSONObject().also { put(mapName, it) }
        obj.put(key, value)
    }

    private fun JSONObject.removeObjectInMap(mapName: String, key: String) {
        val obj = optJSONObject(mapName) ?: return
        obj.remove(key)
    }

    private fun JSONObject.copyObjectSectionFrom(source: JSONObject, key: String) {
        val value = source.optJSONObject(key) ?: return
        put(key, JSONObject(value.toString()))
    }

    private fun JSONObject.toDownloadTaskEntity(): DownloadTaskEntity {
        return DownloadTaskEntity(
            taskId = optString("taskId"),
            appId = optString("appId"),
            status = optString("status"),
            progress = optInt("progress"),
            targetFilePath = optString("targetFilePath"),
            downloadedBytes = optLong("downloadedBytes"),
            totalBytes = optLong("totalBytes"),
            speedBytesPerSec = optLong("speedBytesPerSec"),
            failureCode = optString("failureCode").ifBlank { null },
            failureMessage = optString("failureMessage").ifBlank { null },
            retryCount = optInt("retryCount"),
            downloadUrl = optString("downloadUrl").ifBlank { null },
            tempDirPath = optString("tempDirPath").ifBlank { null },
            eTag = optString("eTag").ifBlank { null },
            lastModified = optString("lastModified").ifBlank { null },
            supportsRange = optBoolean("supportsRange"),
            checksumType = optString("checksumType").ifBlank { null },
            checksumValue = optString("checksumValue").ifBlank { null },
            segmentCount = optInt("segmentCount", 1),
            createdAt = optLong("createdAt"),
            updatedAt = optLong("updatedAt"),
        )
    }

    private companion object {
        /** 结构化本地 store 当前 schema 版本。 */
        const val STRUCTURED_STORE_SCHEMA_VERSION = 1
    }
}
