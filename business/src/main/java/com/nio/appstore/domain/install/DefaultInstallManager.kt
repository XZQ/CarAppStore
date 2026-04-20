package com.nio.appstore.domain.install

import com.nio.appstore.core.installer.InstallEvent
import com.nio.appstore.core.installer.InstallFailureCode
import com.nio.appstore.core.installer.InstallRequest
import com.nio.appstore.core.installer.PackageInstaller
import com.nio.appstore.core.logger.AppLogger
import com.nio.appstore.core.tracker.EventTracker
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.InstallStatus
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.text.BusinessText
import java.io.File

class DefaultInstallManager(
    /** 统一数据入口，负责 APK 路径与安装结果回写。 */
    private val repository: AppRepository,
    /** 安装运行态状态中心。 */
    private val stateCenter: StateCenter,
    /** 安装前置策略中心。 */
    private val policyCenter: PolicyCenter,
    /** 真正执行安装动作的底层组件。 */
    private val packageInstaller: PackageInstaller,
    /** 安装链路日志器。 */
    private val logger: AppLogger,
    /** 安装链路打点器。 */
    private val tracker: EventTracker,
) : InstallManager {

    /**
     * 启动指定应用的安装流程。
     *
     * 该方法负责策略校验、APK 校验以及消费底层安装事件。
     */
    override suspend fun install(appId: String) {
        require(appId.isNotBlank()) { "appId 不能为空" }
        // 安装前先做策略判断，避免在不允许安装时继续进入系统会话。
        val policy = policyCenter.canInstall(appId)
        if (!policy.allow) {
            stateCenter.updateInstall(
                appId,
                InstallStatus.FAILED,
                errorMessage = BusinessText.installRestricted(policy.reason),
                errorCode = InstallFailureCode.POLICY_BLOCKED.name,
            )
            return
        }

        // 安装依赖已经下载完成的本地 APK，因此要先确认路径和文件有效性。
        val apkPath = repository.getDownloadedApk(appId)
        if (apkPath.isNullOrEmpty()) {
            stateCenter.updateDownload(
                appId,
                DownloadStatus.FAILED,
                progress = 0,
                localApkPath = null,
                errorMessage = BusinessText.DOWNLOAD_APK_MISSING,
                errorCode = "APK_MISSING",
            )
            stateCenter.updateInstall(
                appId,
                InstallStatus.FAILED,
                errorMessage = BusinessText.installFailed(InstallFailureCode.APK_MISSING.displayText),
                errorCode = InstallFailureCode.APK_MISSING.name,
            )
            return
        }

        // 准备安装请求时，同时考虑 staged upgrade 的目标版本覆盖。
        val detail = repository.getAppDetail(appId)
        val targetVersion = repository.peekStagedUpgradeVersion(appId) ?: detail.versionName
        val apkFile = File(apkPath)

        logger.d("InstallManager", "install: $appId, apkPath=$apkPath, size=${apkFile.length()}")
        tracker.track("install_start_$appId")
        stateCenter.resetError(appId)

        // 消费安装器事件，并把系统会话阶段翻译成业务层运行态。
        packageInstaller.install(
            InstallRequest(
                appId = appId,
                packageName = detail.packageName,
                targetVersion = targetVersion,
                apkFile = apkFile,
            )
        ) { event ->
            when (event) {
                InstallEvent.Waiting -> {
                    // 安装器进入等待态时，页面先展示“等待安装”。
                    stateCenter.updateInstall(appId, InstallStatus.WAITING)
                }
                is InstallEvent.SessionCreated -> {
                    // 系统会话创建成功后，记录日志并继续保持等待态。
                    logger.d("InstallManager", "session created: ${event.sessionId} for $appId")
                    stateCenter.updateInstall(appId, InstallStatus.WAITING)
                }
                is InstallEvent.PendingUserAction -> {
                    // 进入系统确认阶段后，页面要明确展示“等待用户确认”。
                    logger.d("InstallManager", "session pending user action: ${event.sessionId} for $appId")
                    stateCenter.updateInstall(appId, InstallStatus.PENDING_USER_ACTION)
                }
                InstallEvent.Installing -> {
                    // 系统真正开始安装时切换到安装中状态。
                    stateCenter.updateInstall(appId, InstallStatus.INSTALLING)
                }
                is InstallEvent.Progress -> {
                    // 当前业务层暂不持久化百分比，但仍记录日志并维持安装中状态。
                    stateCenter.updateInstall(appId, InstallStatus.INSTALLING)
                    logger.d("InstallManager", "install progress: $appId -> ${event.progress}%")
                }
                is InstallEvent.Success -> {
                    // 安装成功后要同时更新已安装记录、清理下载任务并同步页面主状态。
                    repository.markInstalled(appId)
                    repository.removeDownloadTask(appId)
                    stateCenter.updateInstall(appId, InstallStatus.INSTALLED, versionName = event.installedVersion)
                    stateCenter.updateDownload(appId, DownloadStatus.COMPLETED, progress = 100, localApkPath = apkPath)
                    tracker.track("install_success_$appId")
                }
                is InstallEvent.Failed -> {
                    // APK 缺失或损坏时，需要同时把下载状态打回失败，提示用户重新下载。
                    if (event.code == InstallFailureCode.APK_MISSING || event.code == InstallFailureCode.APK_INVALID) {
                        repository.clearDownloadedApk(appId)
                        stateCenter.updateDownload(
                            appId,
                            DownloadStatus.FAILED,
                            progress = 0,
                            localApkPath = null,
                            errorMessage = BusinessText.retryDownload(event.message),
                            errorCode = event.code.name,
                        )
                    }
                    // 安装失败统一回写到安装状态，保持错误来源可追踪。
                    stateCenter.updateInstall(
                        appId,
                        InstallStatus.FAILED,
                        errorMessage = BusinessText.installFailed(event.message),
                        errorCode = event.code.name,
                    )
                    tracker.track("install_fail_${event.code.name.lowercase()}_$appId")
                }
            }
        }
    }

    /** 清理指定应用的安装失败态，并恢复到可继续操作的状态。 */
    override suspend fun clearFailed(appId: String) {
        require(appId.isNotBlank()) { "appId 不能为空" }
        val apkPath = repository.getDownloadedApk(appId)
        val apkFile = apkPath?.let { File(it) }
        val hasValidApk = apkFile?.exists() == true && apkFile.length() > 0
        if (hasValidApk) {
            // 本地 APK 仍然可用时，保留下载完成态，让用户可以直接重新安装。
            stateCenter.updateDownload(
                appId,
                DownloadStatus.COMPLETED,
                progress = 100,
                localApkPath = apkPath,
                errorMessage = null,
                errorCode = null,
            )
            stateCenter.updateInstall(
                appId,
                InstallStatus.WAITING,
                errorMessage = null,
                errorCode = null,
            )
        } else {
            // 本地 APK 已经失效时，直接把下载态和安装态都复位，避免误导用户。
            repository.clearDownloadedApk(appId)
            stateCenter.updateInstall(
                appId,
                InstallStatus.NOT_INSTALLED,
                errorMessage = null,
                errorCode = null,
            )
            stateCenter.updateDownload(
                appId,
                DownloadStatus.IDLE,
                progress = 0,
                localApkPath = null,
                errorMessage = null,
                errorCode = null,
            )
        }
        // 最后统一清空错误展示，保证页面从失败态中退出来。
        stateCenter.resetError(appId)
    }
}
