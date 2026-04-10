package com.nio.appstore.data.local.store

import com.nio.appstore.data.local.entity.DownloadArtifactRefEntity
import com.nio.appstore.data.local.entity.DownloadSegmentEntity
import com.nio.appstore.data.local.entity.DownloadTaskEntity
import com.nio.appstore.data.local.entity.InstallSessionEntity
import com.nio.appstore.data.local.entity.InstalledAppEntity
import com.nio.appstore.data.local.entity.SettingsEntity

class InMemoryLocalStoreFacade : LocalStoreFacade {

    /** 内存态的已安装应用表，按 appId 建索引。 */
    private val installedApps = linkedMapOf<String, InstalledAppEntity>()
    /** 内存态的下载任务表，按 appId 建索引。 */
    private val downloadTasks = linkedMapOf<String, DownloadTaskEntity>()
    /** 内存态的下载分片表，按 appId 建索引。 */
    private val downloadSegments = linkedMapOf<String, List<DownloadSegmentEntity>>()
    /** 内存态的下载产物引用表，按 appId 建索引。 */
    private val downloadArtifactRefs = linkedMapOf<String, DownloadArtifactRefEntity>()
    /** 内存态的安装会话表，按 sessionId 建索引。 */
    private val installSessions = linkedMapOf<Int, InstallSessionEntity>()
    /** 内存态的设置表，按 key 建索引。 */
    private val settings = linkedMapOf<String, SettingsEntity>()

    /** 保存已安装应用记录。 */
    override fun saveInstalledApp(entity: InstalledAppEntity) {
        installedApps[entity.appId] = entity
    }

    /** 读取全部已安装应用记录。 */
    override fun getInstalledApps(): List<InstalledAppEntity> = installedApps.values.toList()

    /** 保存下载任务记录。 */
    override fun saveDownloadTask(entity: DownloadTaskEntity) {
        downloadTasks[entity.appId] = entity
    }

    /** 读取指定应用的下载任务记录。 */
    override fun getDownloadTask(appId: String): DownloadTaskEntity? = downloadTasks[appId]

    /** 读取全部下载任务记录，并按更新时间倒序输出。 */
    override fun getAllDownloadTasks(): List<DownloadTaskEntity> = downloadTasks.values.sortedByDescending { it.updatedAt }

    /** 删除下载任务时，同时清理它的分片记录。 */
    override fun removeDownloadTask(appId: String) {
        downloadTasks.remove(appId)
        downloadSegments.remove(appId)
    }

    /** 保存指定应用的下载分片记录。 */
    override fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentEntity>) {
        downloadSegments[appId] = segments
    }

    /** 读取指定应用的下载分片记录。 */
    override fun getDownloadSegments(appId: String): List<DownloadSegmentEntity> = downloadSegments[appId].orEmpty()

    /** 保存下载产物引用记录。 */
    override fun saveDownloadArtifactRef(entity: DownloadArtifactRefEntity) {
        downloadArtifactRefs[entity.appId] = entity
    }

    /** 读取下载产物引用记录。 */
    override fun getDownloadArtifactRef(appId: String): DownloadArtifactRefEntity? = downloadArtifactRefs[appId]

    /** 删除下载产物引用记录。 */
    override fun removeDownloadArtifactRef(appId: String) {
        downloadArtifactRefs.remove(appId)
    }

    /** 保存安装会话记录。 */
    override fun saveInstallSession(entity: InstallSessionEntity) {
        installSessions[entity.sessionId] = entity
    }

    /** 读取全部安装会话记录，并按更新时间倒序输出。 */
    override fun getInstallSessions(): List<InstallSessionEntity> = installSessions.values.sortedByDescending { it.updatedAt }

    /** 保存设置项。 */
    override fun saveSetting(entity: SettingsEntity) {
        settings[entity.key] = entity
    }

    /** 读取指定设置项。 */
    override fun getSetting(key: String): SettingsEntity? = settings[key]

    /** 删除指定设置项。 */
    override fun removeSetting(key: String) {
        settings.remove(key)
    }

    /** 读取全部设置项，并按更新时间倒序输出。 */
    override fun getAllSettings(): List<SettingsEntity> = settings.values.sortedByDescending { it.updatedAt }
}
