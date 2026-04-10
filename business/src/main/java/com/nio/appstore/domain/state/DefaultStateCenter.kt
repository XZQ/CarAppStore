package com.nio.appstore.domain.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

class DefaultStateCenter : StateCenter {
    /** 每个应用对应的状态流，按 appId 建立索引。 */
    private val stateMap = ConcurrentHashMap<String, MutableStateFlow<AppState>>()
    /** 全量状态快照，供列表和聚合层统一监听。 */
    private val allStates = MutableStateFlow<Map<String, AppState>>(emptyMap())

    /** 返回指定应用的状态流，不存在时按默认状态初始化。 */
    override fun observe(appId: String): StateFlow<AppState> =
        stateMap.getOrPut(appId) { MutableStateFlow(StateReducer.reduce(AppState(appId = appId))) }

    /** 读取指定应用的当前状态快照。 */
    override fun snapshot(appId: String): AppState = observe(appId).value

    /** 返回全部应用状态的聚合视图。 */
    override fun observeAll(): StateFlow<Map<String, AppState>> = allStates

    /** 在系统层确认安装成功后，同步状态中心中的安装结果。 */
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

    /** 更新下载维度状态，并保留未传入的历史字段。 */
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

    /** 更新安装维度状态，并在成功时同步已安装版本。 */
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

    /** 更新升级维度状态。 */
    override fun updateUpgrade(appId: String, status: UpgradeStatus, errorMessage: String?, errorCode: String?) {
        mutate(appId) {
            it.copy(
                upgradeStatus = status,
                errorMessage = errorMessage,
                errorCode = errorCode,
            )
        }
    }

    /** 清理指定应用状态上的错误信息。 */
    override fun resetError(appId: String) {
        mutate(appId) { it.copy(errorMessage = null, errorCode = null) }
    }

    /** 在单点入口内完成状态变换、归约和全量快照刷新。 */
    private fun mutate(appId: String, transform: (AppState) -> AppState) {
        val flow = stateMap.getOrPut(appId) { MutableStateFlow(StateReducer.reduce(AppState(appId = appId))) }
        // 所有状态变更都会先经过 reducer，保证状态文案和主动作保持一致。
        val reduced = StateReducer.reduce(transform(flow.value))
        flow.value = reduced
        allStates.value = stateMap.mapValues { entry -> entry.value.value }
    }
}
