package com.xzq.appstore.domain.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DefaultStateCenter : StateCenter {
    /** 每个应用对应的状态流，按 appId 建立索引。 */
    private val stateMap = ConcurrentHashMap<String, MutableStateFlow<AppState>>()

    /** 全量状态快照，供列表和聚合层统一监听。 */
    private val allStates = MutableStateFlow<Map<String, AppState>>(emptyMap())

    /** 保护 mutate 读-改-写与 allStates 刷新的原子性。 */
    private val mutateLock = ReentrantLock()

    /** 返回指定应用的状态流，不存在时按默认状态初始化。 */
    override fun observe(appId: String): StateFlow<AppState> =
        stateMap.computeIfAbsent(appId) { MutableStateFlow(StateReducer.reduce(AppState(appId = appId))) }

    /** 读取指定应用状态的当前快照。 */
    override fun snapshot(appId: String): AppState = observe(appId).value

    /** 返回全部应用状态的聚合视图。 */
    override fun observeAll(): StateFlow<Map<String, AppState>> = allStates

    /** 页面读取系统事实时只同步版本；任务终态由安装、下载和升级执行者更新。 */
    override fun syncInstalled(appId: String, versionName: String, versionCode: Long) {
        mutate(appId) {
            it.copy(
                installStatus = if (it.installStatus == InstallStatus.NOT_INSTALLED) InstallStatus.INSTALLED else it.installStatus,
                installedVersion = versionName,
                installedVersionCode = versionCode,
            )
        }
    }

    override fun syncUpgradeAvailability(appId: String, available: Boolean) {
        mutate(appId) {
            if (it.upgradeStatus == UpgradeStatus.UPGRADING || it.upgradeStatus == UpgradeStatus.FAILED) it
            else it.copy(upgradeStatus = if (available) UpgradeStatus.AVAILABLE else UpgradeStatus.NONE)
        }
    }

    /** 更新下载维度状态，并保留未传入的历史字段。 */
    override fun updateDownload(appId: String, status: DownloadStatus, progress: Int?, localApkPath: String?, errorMessage: String?, errorCode: String?) {
        mutate(appId) {
            it.copy(
                downloadStatus = status,
                progress = progress ?: it.progress,
                // 只有完成态可以保留已验证产物；清理、取消或失败必须丢弃旧路径。
                localApkPath = if (status == DownloadStatus.COMPLETED) localApkPath ?: it.localApkPath else localApkPath,
                errorMessage = errorMessage,
                errorCode = errorCode,
            )
        }
    }

    /** 更新安装维度状态，并在成功时同步已安装版本。 */
    override fun updateInstall(appId: String, status: InstallStatus, versionName: String?, errorMessage: String?, errorCode: String?, versionCode: Long?) {
        mutate(appId) {
            it.copy(installStatus = status,
                installedVersion = if (status == InstallStatus.NOT_INSTALLED) null else versionName ?: it.installedVersion,
                installedVersionCode = if (status == InstallStatus.NOT_INSTALLED) 0L else versionCode ?: it.installedVersionCode,
                errorMessage = errorMessage, errorCode = errorCode)
        }
    }

    /** 更新升级维度状态。 */
    override fun updateUpgrade(appId: String, status: UpgradeStatus, errorMessage: String?, errorCode: String?) {
        mutate(appId) {
            it.copy(upgradeStatus = status, errorMessage = errorMessage, errorCode = errorCode)
        }
    }

    /** 保留业务事实，仅更新动作反馈。 */
    override fun updateError(appId: String, message: String, code: String) {
        mutate(appId) { it.copy(errorMessage = message, errorCode = code) }
    }

    /** 清理指定应用状态上的错误信息。 */
    override fun resetError(appId: String) {
        mutate(appId) { it.copy(errorMessage = null, errorCode = null) }
    }

    /** 在单点入口内完成状态变换、归约和全量快照刷新。同一 appId 的并发 mutate 串行化，避免丢失更新。 */
    private fun mutate(appId: String, transform: (AppState) -> AppState) {
        mutateLock.withLock {
            val flow = stateMap.computeIfAbsent(appId) { MutableStateFlow(StateReducer.reduce(AppState(appId = appId))) }
            // 所有状态变更都会先经过 reducer，保证状态文案和主动作保持一致。
            val reduced = StateReducer.reduce(transform(flow.value))
            flow.value = reduced
            allStates.value = stateMap.mapValues { entry -> entry.value.value }
        }
    }
}
