package com.nio.appstore.domain.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

class DefaultStateCenter : StateCenter {
    private val stateMap = ConcurrentHashMap<String, MutableStateFlow<AppState>>()
    private val allStates = MutableStateFlow<Map<String, AppState>>(emptyMap())

    override fun observe(appId: String): StateFlow<AppState> =
        stateMap.getOrPut(appId) { MutableStateFlow(StateReducer.reduce(AppState(appId = appId))) }

    override fun snapshot(appId: String): AppState = observe(appId).value

    override fun observeAll(): StateFlow<Map<String, AppState>> = allStates

    override fun syncInstalled(appId: String, versionName: String) {
        mutate(appId) {
            it.copy(
                installStatus = InstallStatus.INSTALLED,
                installedVersion = versionName,
                downloadStatus = if (it.downloadStatus == DownloadStatus.RUNNING || it.downloadStatus == DownloadStatus.WAITING) DownloadStatus.IDLE else it.downloadStatus,
                progress = if (it.downloadStatus == DownloadStatus.COMPLETED) 100 else it.progress,
                errorMessage = null,
                errorCode = null,
            )
        }
    }

    override fun updateDownload(appId: String, status: DownloadStatus, progress: Int?, localApkPath: String?, errorMessage: String?, errorCode: String?) {
        mutate(appId) {
            it.copy(
                downloadStatus = status,
                progress = progress ?: it.progress,
                localApkPath = localApkPath ?: it.localApkPath,
                errorMessage = errorMessage,
                errorCode = errorCode,
            )
        }
    }

    override fun updateInstall(appId: String, status: InstallStatus, versionName: String?, errorMessage: String?, errorCode: String?) {
        mutate(appId) {
            it.copy(
                installStatus = status,
                installedVersion = versionName ?: it.installedVersion,
                errorMessage = errorMessage,
                errorCode = errorCode,
            )
        }
    }

    override fun updateUpgrade(appId: String, status: UpgradeStatus, errorMessage: String?, errorCode: String?) {
        mutate(appId) {
            it.copy(
                upgradeStatus = status,
                errorMessage = errorMessage,
                errorCode = errorCode,
            )
        }
    }

    override fun resetError(appId: String) {
        mutate(appId) { it.copy(errorMessage = null, errorCode = null) }
    }

    private fun mutate(appId: String, transform: (AppState) -> AppState) {
        val flow = stateMap.getOrPut(appId) { MutableStateFlow(StateReducer.reduce(AppState(appId = appId))) }
        val reduced = StateReducer.reduce(transform(flow.value))
        flow.value = reduced
        allStates.value = stateMap.mapValues { entry -> entry.value.value }
    }
}
