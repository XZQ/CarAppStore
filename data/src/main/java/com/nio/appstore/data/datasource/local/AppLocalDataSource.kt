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

class AppLocalDataSource(
    context: Context,
    private val localStoreFacade: LocalStoreFacade = InMemoryLocalStoreFacade(),
) {
    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "local_store.json")
    private val downloadDir = File(appContext.filesDir, "downloads").apply { mkdirs() }

    private val installedApps = mutableListOf<InstalledApp>()
    private val downloadedApkPaths = mutableMapOf<String, String>()
    private val stagedUpgradeVersions = mutableMapOf<String, String>()
    private val downloadTasks = mutableMapOf<String, DownloadTaskRecord>()
    private val downloadSegments = mutableMapOf<String, List<DownloadSegmentRecord>>()
    private var downloadPreferences = DownloadPreferences()
    private var policySettings = PolicySettings()

    init {
        loadFromDisk()
    }

    fun getInstalledApps(): List<InstalledApp> = installedApps.toList()

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

    fun isInstalled(appId: String): Boolean = installedApps.any { it.appId == appId }

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

    fun getDownloadedApk(appId: String): String? {
        val facadePath = localStoreFacade.getDownloadArtifactRef(appId)?.apkPath?.takeIf { File(it).exists() }
        val legacyPath = downloadedApkPaths[appId]?.takeIf { File(it).exists() }
        return LocalStoreFallbackPolicy.preferFacade(facadePath, legacyPath)
    }

    fun getOrCreateDownloadFile(appId: String): File = File(downloadDir, "$appId.apk")

    fun getPolicySettings(): PolicySettings {
        val facadeValue = localStoreFacade.getSetting(LocalStoreKeys.POLICY_SETTINGS)?.value?.let { decodePolicySettings(it) }
        return LocalStoreFallbackPolicy.preferFacade(facadeValue, policySettings) ?: PolicySettings()
    }

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

    fun consumeStagedUpgradeVersion(appId: String): String? {
        val facadeKey = LocalStoreKeys.stagedUpgrade(appId)
        val value = localStoreFacade.getSetting(facadeKey)?.value ?: stagedUpgradeVersions.remove(appId)
        localStoreFacade.removeSetting(facadeKey)
        persist()
        return value
    }

    fun peekStagedUpgradeVersion(appId: String): String? {
        return LocalStoreFallbackPolicy.preferFacade(
            localStoreFacade.getSetting(LocalStoreKeys.stagedUpgrade(appId))?.value,
            stagedUpgradeVersions[appId],
        )
    }

    fun saveDownloadTask(record: DownloadTaskRecord) {
        downloadTasks[record.appId] = record
        localStoreFacade.saveDownloadTask(DownloadTaskMapper.toEntity(record))
        persist()
    }

    fun getDownloadTask(appId: String): DownloadTaskRecord? {
        val facadeRecord = localStoreFacade.getDownloadTask(appId)?.let { DownloadTaskMapper.fromEntity(it) }
        return LocalStoreFallbackPolicy.preferFacade(facadeRecord, downloadTasks[appId])
    }

    fun getAllDownloadTasks(): List<DownloadTaskRecord> {
        val facadeTasks = localStoreFacade.getAllDownloadTasks().map { DownloadTaskMapper.fromEntity(it) }
        val legacyTasks = downloadTasks.values.sortedByDescending { it.updatedAt }
        return LocalStoreFallbackPolicy.preferFacadeList(facadeTasks, legacyTasks)
    }

    fun removeDownloadTask(appId: String) {
        downloadTasks.remove(appId)
        downloadSegments.remove(appId)
        localStoreFacade.removeDownloadTask(appId)
        persist()
    }

    fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentRecord>) {
        downloadSegments[appId] = segments
        localStoreFacade.saveDownloadSegments(appId, segments.map { DownloadTaskMapper.toSegmentEntity(it) })
        persist()
    }

    fun getDownloadSegments(appId: String): List<DownloadSegmentRecord> {
        val facadeSegments = localStoreFacade.getDownloadSegments(appId).map { DownloadTaskMapper.fromSegmentEntity(it) }
        val legacySegments = downloadSegments[appId].orEmpty()
        return LocalStoreFallbackPolicy.preferFacadeList(facadeSegments, legacySegments)
    }

    fun clearCompletedDownloadTasks(): Int {
        val targets = downloadTasks.values.filter {
            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELED
        }
        targets.forEach { task ->
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

    fun clearDownloadedApk(appId: String) {
        downloadedApkPaths.remove(appId)
        localStoreFacade.removeDownloadArtifactRef(appId)
        getOrCreateDownloadFile(appId).takeIf { it.exists() }?.delete()
        persist()
    }

    fun getDownloadPreferences(): DownloadPreferences {
        val facadeValue = localStoreFacade.getSetting(LocalStoreKeys.DOWNLOAD_PREFERENCES)?.value?.let { decodeDownloadPreferences(it) }
        return LocalStoreFallbackPolicy.preferFacade(facadeValue, downloadPreferences) ?: DownloadPreferences()
    }

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

    private fun loadFromDisk() {
        if (!storeFile.exists()) return
        runCatching {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
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


    private fun encodeDownloadPreferences(value: DownloadPreferences): String {
        return JSONObject().apply {
            put("autoResumeOnLaunch", value.autoResumeOnLaunch)
            put("autoRetryEnabled", value.autoRetryEnabled)
            put("maxAutoRetryCount", value.maxAutoRetryCount)
        }.toString()
    }

    private fun decodeDownloadPreferences(raw: String): DownloadPreferences {
        val json = JSONObject(raw)
        return DownloadPreferences(
            autoResumeOnLaunch = json.optBoolean("autoResumeOnLaunch", false),
            autoRetryEnabled = json.optBoolean("autoRetryEnabled", true),
            maxAutoRetryCount = json.optInt("maxAutoRetryCount", 2).coerceAtLeast(0),
        )
    }

    private fun encodePolicySettings(value: PolicySettings): String {
        return JSONObject().apply {
            put("wifiConnected", value.wifiConnected)
            put("parkingMode", value.parkingMode)
            put("lowStorageMode", value.lowStorageMode)
        }.toString()
    }

    private fun decodePolicySettings(raw: String): PolicySettings {
        val json = JSONObject(raw)
        return PolicySettings(
            wifiConnected = json.optBoolean("wifiConnected", true),
            parkingMode = json.optBoolean("parkingMode", true),
            lowStorageMode = json.optBoolean("lowStorageMode", false),
        )
    }

    private fun persist() {
        val root = JSONObject()
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
        storeFile.writeText(root.toString(), Charsets.UTF_8)
    }
}
