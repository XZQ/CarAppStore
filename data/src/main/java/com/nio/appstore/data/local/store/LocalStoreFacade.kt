package com.nio.appstore.data.local.store

import com.nio.appstore.data.local.entity.DownloadArtifactRefEntity
import com.nio.appstore.data.local.entity.DownloadSegmentEntity
import com.nio.appstore.data.local.entity.DownloadTaskEntity
import com.nio.appstore.data.local.entity.InstallSessionEntity
import com.nio.appstore.data.local.entity.InstalledAppEntity
import com.nio.appstore.data.local.entity.SettingsEntity

interface LocalStoreFacade {
    /** 保存已安装应用记录。 */
    fun saveInstalledApp(entity: InstalledAppEntity)
    /** 读取全部已安装应用记录。 */
    fun getInstalledApps(): List<InstalledAppEntity>

    /** 保存下载任务记录。 */
    fun saveDownloadTask(entity: DownloadTaskEntity)
    /** 读取指定应用的下载任务记录。 */
    fun getDownloadTask(appId: String): DownloadTaskEntity?
    /** 读取全部下载任务记录。 */
    fun getAllDownloadTasks(): List<DownloadTaskEntity>
    /** 删除指定应用的下载任务记录。 */
    fun removeDownloadTask(appId: String)

    /** 保存指定应用的下载分片记录。 */
    fun saveDownloadSegments(appId: String, segments: List<DownloadSegmentEntity>)
    /** 读取指定应用的下载分片记录。 */
    fun getDownloadSegments(appId: String): List<DownloadSegmentEntity>

    /** 保存下载产物引用记录。 */
    fun saveDownloadArtifactRef(entity: DownloadArtifactRefEntity)
    /** 读取下载产物引用记录。 */
    fun getDownloadArtifactRef(appId: String): DownloadArtifactRefEntity?
    /** 删除下载产物引用记录。 */
    fun removeDownloadArtifactRef(appId: String)

    /** 保存安装会话记录。 */
    fun saveInstallSession(entity: InstallSessionEntity)
    /** 读取全部安装会话记录。 */
    fun getInstallSessions(): List<InstallSessionEntity>

    /** 保存设置项。 */
    fun saveSetting(entity: SettingsEntity)
    /** 读取指定设置项。 */
    fun getSetting(key: String): SettingsEntity?
    /** 删除指定设置项。 */
    fun removeSetting(key: String)
    /** 读取全部设置项。 */
    fun getAllSettings(): List<SettingsEntity>
}
