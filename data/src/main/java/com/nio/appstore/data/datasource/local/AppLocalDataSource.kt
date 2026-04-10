package com.nio.appstore.data.datasource.local

import android.content.Context
import com.nio.appstore.data.model.DownloadPreferences
import com.nio.appstore.data.model.DownloadSegmentRecord
import com.nio.appstore.data.model.DownloadTaskRecord
import com.nio.appstore.data.model.InstalledApp
import com.nio.appstore.data.local.entity.DownloadArtifactRefEntity
import com.nio.appstore.data.local.entity.InstalledAppEntity
import com.nio.appstore.data.local.mapper.DownloadTaskMapper
import com.nio.appstore.data.local.store.InMemoryLocalStoreFacade
import com.nio.appstore.data.local.store.LocalStoreFacade
import com.nio.appstore.data.local.store.LocalStoreFallbackPolicy
import com.nio.appstore.data.local.store.LocalStoreKeys
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.domain.state.DownloadStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AppLocalDataSource(
    context: Context,
    /** 统一结构化本地存储入口。 */
    private val localStoreFacade: LocalStoreFacade = InMemoryLocalStoreFacade(),
) {
    /** 当前模块统一使用的应用级 Context。 */
    private val appContext = context.applicationContext
    /** 兼容旧版本格式的 legacy 本地存储文件。 */
    private val storeFile = File(appContext.filesDir, "local_store.json")
    /** 下载产物默认目录。 */
    private val downloadDir = File(appContext.filesDir, "downloads").apply { mkdirs() }

    /** 已安装应用的 legacy 内存镜像。 */
    private val installedApps = mutableListOf<InstalledApp>()
    /** 已下载 APK 路径的 legacy 内存镜像。 */
    private val downloadedApkPaths = mutableMapOf<String, String>()
    /** staged upgrade 版本号的 legacy 内存镜像。 */
    private val stagedUpgradeVersions = mutableMapOf<String, String>()
    /** 下载任务的 legacy 内存镜像。 */
    private val downloadTasks = mutableMapOf<String, DownloadTaskRecord>()
    /** 下载分片的 legacy 内存镜像。 */
    private val downloadSegments = mutableMapOf<String, List<DownloadSegmentRecord>>()
    /** 下载偏好的 legacy 内存镜像。 */
    private var downloadPreferences = DownloadPreferences()
    /** 策略设置的 legacy 内存镜像。 */
    private var policySettings = PolicySettings()
    /** 保护 legacy fallback 文件读写的并发锁。 */
    private val legacyStoreLock = ReentrantLock()

    init {
        loadFromDisk()
    }

    /** 获取已安装应用列表。 */
    fun getInstalledApps(): List<InstalledApp> = installedApps.toList()

    /** 保存已安装应用，并同步到结构化存储和 legacy fallback。 */
    fun saveInstalledApp(app: InstalledApp) {
        installedApps.removeAll { it.appId == app.appId }
        installedApps.add(app)
        localStoreFacade.saveInstalledApp(
            InstalledAppEntity(
                appId = app.appId,
                packageName = app.packageName,
                name = app.name,
                versionName = app.versionName,
            )
        )
        persist()
    }

    /** 判断指定应用是否已经安装。 */
    fun isInstalled(appId: String): Boolean = installedApps.any { it.appId == appId }

    /** 保存已下载 APK 的路径引用。 */
    fun saveDownloadedApk(appId: String, apkPath: String) {
        downloadedApkPaths[appId] = apkPath
        val file = File(apkPath)
        localStoreFacade.saveDownloadArtifactRef(
            DownloadArtifactRefEntity(
                appId = appId,
                apkPath = apkPath,
                fileSize = if (file.exists()) file.length() else 0L,
                fileExists = file.exists(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        persist()
    }

    /** 获取仍然有效的 APK 路径。 */
    fun getDownloadedApk(appId: String): String? {
        val facadePath = localStoreFacade.getDownloadArtifactRef(appId)?.apkPath?.takeIf { File(it).exists() }
        val legacyPath = downloadedApkPaths[appId]?.takeIf { File(it).exists() }
        return LocalStoreFallbackPolicy.preferFacade(facadePath, legacyPath)
    }

    /** 获取指定应用默认的下载目标文件。 */
    fun getOrCreateDownloadFile(appId: String): File = File(downloadDir, "$appId.apk")

    /** 获取当前策略设置。 */
    fun getPolicySettings(): PolicySettings {
        val facadeValue = localStoreFacade.getSetting(LocalStoreKeys.POLICY_SETTINGS)?.value?.let { decodePolicySettings(it) }
        return LocalStoreFallbackPolicy.preferFacade(facadeValue, policySettings) ?: PolicySettings()
    }

    /** 保存策略设置。 */
    fun savePolicySettings(settings: PolicySettings) {
        policySettings = settings
        localStoreFacade.saveSetting(
            com.nio.appstore.data.local.entity.SettingsEntity(
                key = LocalStoreKeys.POLICY_SETTINGS,
                value = encodePolicySettings(settings),
                updatedAt = System.currentTimeMillis(),
            )
        )
        persist()
    }

    /** 保存 staged upgrade 的目标版本。 */
    fun stageUpgradeVersion(appId: String, versionName: String) {
        stagedUpgradeVersions[appId] = versionName
        localStoreFacade.saveSetting(
            com.nio.appstore.data.local.entity.SettingsEntity(
                key = LocalStoreKeys.stagedUpgrade(appId),
                value = versionName,
                updatedAt = System.currentTimeMillis(),
            )
        )
        persist()
    }

    /** 读取并消费 staged upgrade 版本号。 */
    fun consumeStagedUpgradeVersion(appId: String): String? {
        val facadeKey = LocalStoreKeys.stagedUpgrade(appId)
        val value = localStoreFacade.getSetting(facadeKey)?.value ?: stagedUpgradeVersions.remove(appId)
        localStoreFacade.removeSetting(facadeKey)
        persist()
        return value
    }

    /** 只读取 staged upgrade 版本号，不执行消费。 */
    fun peekStagedUpgradeVersion(appId: String): String? {
        return LocalStoreFallbackPolicy.preferFacade(
            localStoreFacade.getSetting(LocalStoreKeys.stagedUpgrade(appId))?.value,
            stagedUpgradeVersions[appId],
        )
    }

    /** 保存下载任务记录。 */
    fun saveDownloadTask(record: DownloadTaskRecord) {
        downloadTasks[record.appId] = record
        localStoreFacade.saveDownloadTask(DownloadTaskMapper.toEntity(record))
        persist()
    }

    /** 获取指定应用的下载任务记录。 */
    fun getDownloadTask(appId: String): DownloadTaskRecord? {
        val facadeRecord = localStoreFacade.getDownloadTask(appId)?.let { DownloadTaskMapper.fromEntity(it) }
        return LocalStoreFallbackPolicy.preferFacade(facadeRecord, downloadTasks[appId])
    }

    /** 获取所有下载任务记录。 */
    fun getAllDownloadTasks(): List<DownloadTaskRecord> {
        val facadeTasks = localStoreFacade.getAllDownloadTasks().map { DownloadTaskMapper.fromEntity(it) }
        val legacyTasks = downloadTasks.values.sortedByDescending { it.updatedAt }
        return LocalStoreFallbackPolicy.preferFacadeList(facadeTasks, legacyTasks)
    }

    /** 删除指定应用的下载任务。 */
    fun removeDownloadTask(appId: String) {
        downloadTasks.remove(appId)
        downloadSegments.remove(appId)
        localStoreFacade.removeDownloadTask(appId)
        persist()
    }

    /** 保存指定应用的下载分片记录。 */
    fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) {
        downloadSegments[appId] = segments
        localStoreFacade.saveDownloadSegments(appId, segments.map { DownloadTaskMapper.toSegmentEntity(it) })
        persist()
    }

    /** 获取指定应用的下载分片记录。 */
    fun getDownloadSegments(appId: String): List<DownloadSegmentRecord> {
        val facadeSegments = localStoreFacade.getDownloadSegments(appId).map { DownloadTaskMapper.fromSegmentEntity(it) }
        val legacySegments = downloadSegments[appId].orEmpty()
        return LocalStoreFallbackPolicy.preferFacadeList(facadeSegments, legacySegments)
    }

    /** 清理所有已完成或已取消的下载任务。 */
    fun clearCompletedDownloadTasks(): Int {
        val targets = downloadTasks.values.filter {
            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELED
        }
        targets.forEach { task ->
            // 已取消任务的 APK 不能再复用，需要顺带清理本地文件引用。
            if (task.status == DownloadStatus.CANCELED) {
                downloadedApkPaths.remove(task.appId)
                localStoreFacade.removeDownloadArtifactRef(task.appId)
                getOrCreateDownloadFile(task.appId).takeIf { it.exists() }?.delete()
            }
            downloadTasks.remove(task.appId)
            downloadSegments.remove(task.appId)
            localStoreFacade.removeDownloadTask(task.appId)
        }
        if (targets.isNotEmpty()) persist()
        return targets.size
    }

    /** 删除指定应用的本地 APK 产物和路径引用。 */
    fun clearDownloadedApk(appId: String) {
        downloadedApkPaths.remove(appId)
        localStoreFacade.removeDownloadArtifactRef(appId)
        getOrCreateDownloadFile(appId).takeIf { it.exists() }?.delete()
        persist()
    }

    /** 获取下载偏好设置。 */
    fun getDownloadPreferences(): DownloadPreferences {
        val facadeValue = localStoreFacade.getSetting(LocalStoreKeys.DOWNLOAD_PREFERENCES)?.value?.let { decodeDownloadPreferences(it) }
        return LocalStoreFallbackPolicy.preferFacade(facadeValue, downloadPreferences) ?: DownloadPreferences()
    }

    /** 保存下载偏好设置。 */
    fun saveDownloadPreferences(preferences: DownloadPreferences) {
        downloadPreferences = preferences
        localStoreFacade.saveSetting(
            com.nio.appstore.data.local.entity.SettingsEntity(
                key = LocalStoreKeys.DOWNLOAD_PREFERENCES,
                value = encodeDownloadPreferences(preferences),
                updatedAt = System.currentTimeMillis(),
            )
        )
        persist()
    }

    /** 从 legacy fallback 文件恢复当前内存镜像。 */
    private fun loadFromDisk() {
        legacyStoreLock.withLock {
            if (!storeFile.exists()) return
            runCatching {
                val root = JSONObject(storeFile.readText(Charsets.UTF_8))
                // 依次恢复已安装应用、下载产物、下载任务、分片和设置项，保持旧数据可读。
                installedApps.clear()
                root.optJSONArray("installedApps")?.let { array ->
                    repeat(array.length()) { index ->
                        val item = array.getJSONObject(index)
                        installedApps.add(
                            InstalledApp(
                                appId = item.optString("appId"),
                                packageName = item.optString("packageName"),
                                name = item.optString("name"),
                                versionName = item.optString("versionName"),
                            )
                        )
                    }
                }

                downloadedApkPaths.clear()
                root.optJSONObject("downloadedApkPaths")?.let { obj ->
                    obj.keys().forEach { key -> downloadedApkPaths[key] = obj.optString(key) }
                }

                stagedUpgradeVersions.clear()
                root.optJSONObject("stagedUpgradeVersions")?.let { obj ->
                    obj.keys().forEach { key -> stagedUpgradeVersions[key] = obj.optString(key) }
                }

                downloadTasks.clear()
                root.optJSONArray("downloadTasks")?.let { array ->
                    repeat(array.length()) { index ->
                        val item = array.getJSONObject(index)
                        val record = DownloadTaskRecord(
                            taskId = item.optString("taskId"),
                            appId = item.optString("appId"),
                            status = runCatching { DownloadStatus.valueOf(item.optString("status")) }.getOrElse { DownloadStatus.IDLE },
                            progress = item.optInt("progress"),
                            targetFilePath = item.optString("targetFilePath"),
                            downloadedBytes = item.optLong("downloadedBytes"),
                            totalBytes = item.optLong("totalBytes"),
                            speedBytesPerSec = item.optLong("speedBytesPerSec"),
                            failureCode = item.optString("failureCode").ifBlank { null },
                            failureMessage = item.optString("failureMessage").ifBlank { null },
                            retryCount = item.optInt("retryCount", 0),
                            downloadUrl = item.optString("downloadUrl").ifBlank { null },
                            tempDirPath = item.optString("tempDirPath").ifBlank { null },
                            eTag = item.optString("eTag").ifBlank { null },
                            lastModified = item.optString("lastModified").ifBlank { null },
                            supportsRange = item.optBoolean("supportsRange", false),
                            checksumType = item.optString("checksumType").ifBlank { null },
                            checksumValue = item.optString("checksumValue").ifBlank { null },
                            segmentCount = item.optInt("segmentCount", 1).coerceAtLeast(1),
                            createdAt = item.optLong("createdAt", item.optLong("updatedAt")),
                            updatedAt = item.optLong("updatedAt"),
                        )
                        downloadTasks[record.appId] = record
                    }
                }

                downloadSegments.clear()
                root.optJSONObject("downloadSegments")?.let { obj ->
                    obj.keys().forEach { appId ->
                        val arr = obj.optJSONArray(appId) ?: JSONArray()
                        val list = mutableListOf<DownloadSegmentRecord>()
                        repeat(arr.length()) { index ->
                            val item = arr.getJSONObject(index)
                            list += DownloadSegmentRecord(
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
                        }
                        downloadSegments[appId] = list
                    }
                }

                val prefObj = root.optJSONObject("downloadPreferences")
                if (prefObj != null) {
                    downloadPreferences = DownloadPreferences(
                        autoResumeOnLaunch = prefObj.optBoolean("autoResumeOnLaunch", false),
                        autoRetryEnabled = prefObj.optBoolean("autoRetryEnabled", true),
                        maxAutoRetryCount = prefObj.optInt("maxAutoRetryCount", 2).coerceAtLeast(0),
                    )
                }

                val policyObj = root.optJSONObject("policySettings")
                if (policyObj != null) {
                    policySettings = PolicySettings(
                        wifiConnected = policyObj.optBoolean("wifiConnected", true),
                        parkingMode = policyObj.optBoolean("parkingMode", true),
                        lowStorageMode = policyObj.optBoolean("lowStorageMode", false),
                    )
                }
            }
        }
    }


    /** 将下载偏好编码为 JSON 字符串。 */
    private fun encodeDownloadPreferences(value: DownloadPreferences): String {
        return JSONObject().apply {
            put("autoResumeOnLaunch", value.autoResumeOnLaunch)
            put("autoRetryEnabled", value.autoRetryEnabled)
            put("maxAutoRetryCount", value.maxAutoRetryCount)
        }.toString()
    }

    /** 从 JSON 字符串解码下载偏好。 */
    private fun decodeDownloadPreferences(raw: String): DownloadPreferences {
        val json = JSONObject(raw)
        return DownloadPreferences(
            autoResumeOnLaunch = json.optBoolean("autoResumeOnLaunch", false),
            autoRetryEnabled = json.optBoolean("autoRetryEnabled", true),
            maxAutoRetryCount = json.optInt("maxAutoRetryCount", 2).coerceAtLeast(0),
        )
    }

    /** 将策略设置编码为 JSON 字符串。 */
    private fun encodePolicySettings(value: PolicySettings): String {
        return JSONObject().apply {
            put("wifiConnected", value.wifiConnected)
            put("parkingMode", value.parkingMode)
            put("lowStorageMode", value.lowStorageMode)
        }.toString()
    }

    /** 从 JSON 字符串解码策略设置。 */
    private fun decodePolicySettings(raw: String): PolicySettings {
        val json = JSONObject(raw)
        return PolicySettings(
            wifiConnected = json.optBoolean("wifiConnected", true),
            parkingMode = json.optBoolean("parkingMode", true),
            lowStorageMode = json.optBoolean("lowStorageMode", false),
        )
    }

    /** 将当前 legacy 内存镜像持久化回兼容文件。 */
    private fun persist() {
        legacyStoreLock.withLock {
            val root = JSONObject()
            root.put("schemaVersion", LEGACY_STORE_SCHEMA_VERSION)
            root.put("installedApps", JSONArray().apply {
                installedApps.forEach { app ->
                    put(JSONObject().apply {
                        put("appId", app.appId)
                        put("packageName", app.packageName)
                        put("name", app.name)
                        put("versionName", app.versionName)
                    })
                }
            })
            root.put("downloadedApkPaths", JSONObject().apply {
                downloadedApkPaths.forEach { (appId, path) -> put(appId, path) }
            })
            root.put("stagedUpgradeVersions", JSONObject().apply {
                stagedUpgradeVersions.forEach { (appId, version) -> put(appId, version) }
            })
            root.put("downloadTasks", JSONArray().apply {
                downloadTasks.values.forEach { task ->
                    put(JSONObject().apply {
                        put("taskId", task.taskId)
                        put("appId", task.appId)
                        put("status", task.status.name)
                        put("progress", task.progress)
                        put("targetFilePath", task.targetFilePath)
                        put("downloadedBytes", task.downloadedBytes)
                        put("totalBytes", task.totalBytes)
                        put("speedBytesPerSec", task.speedBytesPerSec)
                        put("failureCode", task.failureCode)
                        put("failureMessage", task.failureMessage)
                        put("retryCount", task.retryCount)
                        put("downloadUrl", task.downloadUrl)
                        put("tempDirPath", task.tempDirPath)
                        put("eTag", task.eTag)
                        put("lastModified", task.lastModified)
                        put("supportsRange", task.supportsRange)
                        put("checksumType", task.checksumType)
                        put("checksumValue", task.checksumValue)
                        put("segmentCount", task.segmentCount)
                        put("createdAt", task.createdAt)
                        put("updatedAt", task.updatedAt)
                    })
                }
            })
            root.put("downloadSegments", JSONObject().apply {
                downloadSegments.forEach { (appId, segments) ->
                    put(appId, JSONArray().apply {
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
            })
            root.put("downloadPreferences", JSONObject().apply {
                put("autoResumeOnLaunch", downloadPreferences.autoResumeOnLaunch)
                put("autoRetryEnabled", downloadPreferences.autoRetryEnabled)
                put("maxAutoRetryCount", downloadPreferences.maxAutoRetryCount)
            })
            root.put("policySettings", JSONObject().apply {
                put("wifiConnected", policySettings.wifiConnected)
                put("parkingMode", policySettings.parkingMode)
                put("lowStorageMode", policySettings.lowStorageMode)
            })
            storeFile.parentFile?.mkdirs()
            // 先写临时文件再替换正式文件，尽量降低写入中断造成的损坏风险。
            val tempFile = File(storeFile.parentFile, storeFile.name + LEGACY_TEMP_FILE_SUFFIX)
            tempFile.writeText(root.toString(), Charsets.UTF_8)
            if (!tempFile.renameTo(storeFile)) {
                tempFile.copyTo(storeFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    private companion object {
        /** legacy fallback 文件当前 schema 版本。 */
        const val LEGACY_STORE_SCHEMA_VERSION = 1

        /** legacy fallback 临时文件后缀。 */
        const val LEGACY_TEMP_FILE_SUFFIX = ".tmp"
    }
}
