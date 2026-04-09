package com.nio.appstore.data.local.store

import com.nio.appstore.data.local.entity.DownloadArtifactRefEntity
import com.nio.appstore.data.local.entity.DownloadSegmentEntity
import com.nio.appstore.data.local.entity.DownloadTaskEntity
import com.nio.appstore.data.local.entity.InstallSessionEntity
import com.nio.appstore.data.local.entity.InstalledAppEntity
import com.nio.appstore.data.local.entity.SettingsEntity

interface LocalStoreFacade {
    fun saveInstalledApp(entity: InstalledAppEntity)
    fun getInstalledApps(): List<InstalledAppEntity>

    fun saveDownloadTask(entity: DownloadTaskEntity)
    fun getDownloadTask(appId: String): DownloadTaskEntity?
    fun getAllDownloadTasks(): List<DownloadTaskEntity>
    fun removeDownloadTask(appId: String)

    fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentEntity>)
    fun getDownloadSegments(appId: String): List<DownloadSegmentEntity>

    fun saveDownloadArtifactRef(entity: DownloadArtifactRefEntity)
    fun getDownloadArtifactRef(appId: String): DownloadArtifactRefEntity?
    fun removeDownloadArtifactRef(appId: String)

    fun saveInstallSession(entity: InstallSessionEntity)
    fun getInstallSessions(): List<InstallSessionEntity>

    fun saveSetting(entity: SettingsEntity)
    fun getSetting(key: String): SettingsEntity?
    fun removeSetting(key: String)
    fun getAllSettings(): List<SettingsEntity>
}
