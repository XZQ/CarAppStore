package com.nio.appstore.data.local.store

import com.nio.appstore.data.local.entity.DownloadArtifactRefEntity
import com.nio.appstore.data.local.entity.DownloadSegmentEntity
import com.nio.appstore.data.local.entity.DownloadTaskEntity
import com.nio.appstore.data.local.entity.InstallSessionEntity
import com.nio.appstore.data.local.entity.InstalledAppEntity
import com.nio.appstore.data.local.entity.SettingsEntity

class InMemoryLocalStoreFacade : LocalStoreFacade {

    private val installedApps = linkedMapOf<String, InstalledAppEntity>()
    private val downloadTasks = linkedMapOf<String, DownloadTaskEntity>()
    private val downloadSegments = linkedMapOf<String, List<DownloadSegmentEntity>>()
    private val downloadArtifactRefs = linkedMapOf<String, DownloadArtifactRefEntity>()
    private val installSessions = linkedMapOf<Int, InstallSessionEntity>()
    private val settings = linkedMapOf<String, SettingsEntity>()

    override fun saveInstalledApp(entity: InstalledAppEntity) {
        installedApps[entity.appId] = entity
    }

    override fun getInstalledApps(): List<InstalledAppEntity> = installedApps.values.toList()

    override fun saveDownloadTask(entity: DownloadTaskEntity) {
        downloadTasks[entity.appId] = entity
    }

    override fun getDownloadTask(appId: String): DownloadTaskEntity? = downloadTasks[appId]

    override fun getAllDownloadTasks(): List<DownloadTaskEntity> = downloadTasks.values.sortedByDescending { it.updatedAt }

    override fun removeDownloadTask(appId: String) {
        downloadTasks.remove(appId)
        downloadSegments.remove(appId)
    }

    override fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentEntity>) {
        downloadSegments[appId] = segments
    }

    override fun getDownloadSegments(appId: String): List<DownloadSegmentEntity> = downloadSegments[appId].orEmpty()

    override fun saveDownloadArtifactRef(entity: DownloadArtifactRefEntity) {
        downloadArtifactRefs[entity.appId] = entity
    }

    override fun getDownloadArtifactRef(appId: String): DownloadArtifactRefEntity? = downloadArtifactRefs[appId]

    override fun removeDownloadArtifactRef(appId: String) {
        downloadArtifactRefs.remove(appId)
    }

    override fun saveInstallSession(entity: InstallSessionEntity) {
        installSessions[entity.sessionId] = entity
    }

    override fun getInstallSessions(): List<InstallSessionEntity> = installSessions.values.sortedByDescending { it.updatedAt }

    override fun saveSetting(entity: SettingsEntity) {
        settings[entity.key] = entity
    }

    override fun getSetting(key: String): SettingsEntity? = settings[key]

    override fun removeSetting(key: String) {
        settings.remove(key)
    }

    override fun getAllSettings(): List<SettingsEntity> = settings.values.sortedByDescending { it.updatedAt }
}
