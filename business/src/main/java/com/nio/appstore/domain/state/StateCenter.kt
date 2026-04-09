package com.nio.appstore.domain.state

import kotlinx.coroutines.flow.StateFlow

interface StateCenter {
    fun observe(appId: String): StateFlow<AppState>
    fun snapshot(appId: String): AppState
    fun observeAll(): StateFlow<Map<String, AppState>>

    fun syncInstalled(appId: String, versionName: String)
    fun updateDownload(
        appId: String,
        status: DownloadStatus,
        progress: Int? = null,
        localApkPath: String? = null,
        errorMessage: String? = null,
        errorCode: String? = null,
    )
    fun updateInstall(
        appId: String,
        status: InstallStatus,
        versionName: String? = null,
        errorMessage: String? = null,
        errorCode: String? = null,
    )
    fun updateUpgrade(appId: String, status: UpgradeStatus, errorMessage: String? = null, errorCode: String? = null)
    fun resetError(appId: String)
}
