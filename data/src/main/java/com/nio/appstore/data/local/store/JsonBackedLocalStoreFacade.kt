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
    /** 结构化本地数据文件路径。 */
    private val storeFile: File,
) : LocalStoreFacade {

    /** 结构化本地数据文件的统一版本化存储入口。 */
    private val jsonStore = VersionedJsonStore(
        storeFile = storeFile,
        schemaVersion = STRUCTURED_STORE_SCHEMA_VERSION,
        defaultRootFactory = ::createEmptyRoot,
        migration = ::migrateRoot,
    )

    /** 保存已安装应用记录到结构化 store。 */
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

    /** 读取全部已安装应用记录。 */
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

    /** 保存下载任务记录到结构化 store。 */
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

    /** 读取指定应用的下载任务记录。 */
    override fun getDownloadTask(appId: String): DownloadTaskEntity? {
        val value = readMap("downloadTasks")[appId] ?: return null
        return value.toDownloadTaskEntity()
    }

    /** 读取全部下载任务记录，并按更新时间倒序输出。 */
    override fun getAllDownloadTasks(): List<DownloadTaskEntity> {
        return readMap("downloadTasks").values
            .map { it.toDownloadTaskEntity() }
            .sortedByDescending { it.updatedAt }
    }

    /** 删除下载任务时，同时清理其分片记录。 */
    override fun removeDownloadTask(appId: String) {
        updateStore { root ->
            root.removeObjectInMap("downloadTasks", appId)
            root.removeObjectInMap("downloadSegments", appId)
        }
    }

    /** 保存指定应用的下载分片记录。 */
    override fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentEntity>) {
        updateStore { root ->
            val arr = JSONArray()
            // 分片列表按数组形式整体写入，便于冷启动时恢复原始顺序。
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

    /** 读取指定应用的下载分片记录。 */
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

    /** 保存下载产物引用记录。 */
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

    /** 读取下载产物引用记录。 */
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

    /** 删除下载产物引用记录。 */
    override fun removeDownloadArtifactRef(appId: String) {
        updateStore { root -> root.removeObjectInMap("downloadArtifactRefs", appId) }
    }

    /** 保存安装会话记录。 */
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

    /** 读取全部安装会话记录，并按更新时间倒序输出。 */
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

    /** 保存设置项。 */
    override fun saveSetting(entity: SettingsEntity) {
        updateStore { root ->
            root.putObjectInMap("settings", entity.key, JSONObject().apply {
                put("key", entity.key)
                put("value", entity.value)
                put("updatedAt", entity.updatedAt)
            })
        }
    }

    /** 读取指定设置项。 */
    override fun getSetting(key: String): SettingsEntity? {
        val value = readMap("settings")[key] ?: return null
        return SettingsEntity(
            key = value.optString("key"),
            value = value.optString("value"),
            updatedAt = value.optLong("updatedAt"),
        )
    }

    /** 删除指定设置项。 */
    override fun removeSetting(key: String) {
        updateStore { root -> root.removeObjectInMap("settings", key) }
    }

    /** 读取全部设置项，并按更新时间倒序输出。 */
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

    /** 读取结构化 store 的当前根节点。 */
    private fun readRoot(): JSONObject = jsonStore.read { it }

    /** 创建结构化 store 的默认根节点。 */
    private fun createEmptyRoot(): JSONObject = JSONObject().apply {
        put("installedApps", JSONObject())
        put("downloadTasks", JSONObject())
        put("downloadSegments", JSONObject())
        put("downloadArtifactRefs", JSONObject())
        put("installSessions", JSONObject())
        put("settings", JSONObject())
    }

    /** 在统一入口内执行 store 更新。 */
    private fun updateStore(block: (JSONObject) -> Unit) {
        jsonStore.update { root -> block(root) }
    }

    /** 将旧格式结构化数据迁移为当前 schema。 */
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

    /** 读取对象 map 结构。 */
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

    /** 读取数组 map 结构。 */
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

    /** 向指定对象 map 中写入一个对象值。 */
    private fun JSONObject.putObjectInMap(mapName: String, key: String, value: JSONObject) {
        val obj = optJSONObject(mapName) ?: JSONObject().also { put(mapName, it) }
        obj.put(key, value)
    }

    /** 向指定对象 map 中写入一个数组值。 */
    private fun JSONObject.putArrayInMap(mapName: String, key: String, value: JSONArray) {
        val obj = optJSONObject(mapName) ?: JSONObject().also { put(mapName, it) }
        obj.put(key, value)
    }

    /** 从指定对象 map 中移除一个键。 */
    private fun JSONObject.removeObjectInMap(mapName: String, key: String) {
        val obj = optJSONObject(mapName) ?: return
        obj.remove(key)
    }

    /** 从旧根节点复制一个对象 section。 */
    private fun JSONObject.copyObjectSectionFrom(source: JSONObject, key: String) {
        val value = source.optJSONObject(key) ?: return
        put(key, JSONObject(value.toString()))
    }

    /** 把 JSON 对象解码为下载任务实体。 */
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
