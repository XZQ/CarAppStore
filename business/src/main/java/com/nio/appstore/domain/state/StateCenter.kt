package com.nio.appstore.domain.state

import kotlinx.coroutines.flow.StateFlow

interface StateCenter {
    /** 监听指定应用的运行态变化。 */
    fun observe(appId: String): StateFlow<AppState>
    /** 读取指定应用的当前状态快照。 */
    fun snapshot(appId: String): AppState
    /** 监听全部应用的状态快照。 */
    fun observeAll(): StateFlow<Map<String, AppState>>

    /** 在系统确认已安装后同步安装状态。 */
    fun syncInstalled(appId: String, versionName: String)
    /** 更新下载子状态。 */
    fun updateDownload(
        appId: String,
        status: DownloadStatus,
        progress: Int? = null,
        localApkPath: String? = null,
        errorMessage: String? = null,
        errorCode: String? = null,
    )
    /** 更新安装子状态。 */
    fun updateInstall(
        appId: String,
        status: InstallStatus,
        versionName: String? = null,
        errorMessage: String? = null,
        errorCode: String? = null,
    )
    /** 更新升级子状态。 */
    fun updateUpgrade(appId: String, status: UpgradeStatus, errorMessage: String? = null, errorCode: String? = null)
    /** 清理指定应用当前状态上的错误信息。 */
    fun resetError(appId: String)
}
