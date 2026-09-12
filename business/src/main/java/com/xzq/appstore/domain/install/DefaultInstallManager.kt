package com.xzq.appstore.domain.install

import com.xzq.appstore.core.installer.InstallEvent
import com.xzq.appstore.core.installer.InstallFailureCode
import com.xzq.appstore.core.installer.InstallRequest
import com.xzq.appstore.core.installer.PackageInstaller
import com.xzq.appstore.core.logger.AppLogger
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.data.model.ClientPlatformCapabilities
import com.xzq.appstore.data.repository.AppRepository
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.state.DownloadStatus
import com.xzq.appstore.domain.state.InstallStatus
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.text.BusinessText
import java.io.File
import kotlinx.coroutines.CancellationException

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
    /** 当前客户端平台能力，用于阻止安装其他平台产物。 */
    private val platformCapabilities: ClientPlatformCapabilities = ClientPlatformCapabilities(),
    private val artifactAccess: ApkArtifactAccess = ApkArtifactAccess(),
) : InstallManager {

    /**
     * 启动指定应用的安装流程。
     *
     * 该方法负责策略校验、APK 校验以及消费底层安装事件。
     */
    override suspend fun install(appId: String) {
        require(appId.isNotBlank()) { "appId 不能为空" }
        artifactAccess.tryUse(appId) {
            try {
                installInternal(appId)
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (failure: Exception) {
                logger.w("InstallManager", "Unable to prepare or execute installation: $appId", failure)
                stateCenter.updateInstall(appId, InstallStatus.FAILED,
                    errorMessage = BusinessText.installFailed(InstallFailureCode.UNKNOWN.displayText),
                    errorCode = InstallFailureCode.UNKNOWN.name)
            }
        }
    }

    private suspend fun installInternal(appId: String) {
        require(appId.isNotBlank()) { "appId 不能为空" }
        if (stateCenter.snapshot(appId).downloadStatus in setOf(DownloadStatus.RUNNING, DownloadStatus.WAITING)) return
        val detail = repository.getAppDetail(appId)
        if (!platformCapabilities.supports(detail.supportedPlatforms)) {
            stateCenter.updateInstall(
                appId,
                InstallStatus.FAILED,
                errorMessage = BusinessText.STATUS_PLATFORM_UNSUPPORTED,
                errorCode = PLATFORM_UNSUPPORTED_ERROR_CODE,
            )
            return
        }
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
            invalidateDownloadedApk(appId, InstallFailureCode.APK_MISSING, BusinessText.DOWNLOAD_APK_MISSING)
            stateCenter.updateInstall(
                appId,
                InstallStatus.FAILED,
                errorMessage = BusinessText.installFailed(InstallFailureCode.APK_MISSING.displayText),
                errorCode = InstallFailureCode.APK_MISSING.name,
            )
            return
        }

        // 准备安装请求时，同时考虑 staged upgrade 的目标版本覆盖。
        val targetVersion = repository.peekStagedUpgradeVersion(appId) ?: detail.versionName
        val apkFile = File(apkPath)
        val spacePolicy = policyCenter.canInstall(appId, apkFile.length())
        if (!spacePolicy.allow) {
            stateCenter.updateInstall(appId, InstallStatus.FAILED, errorMessage = BusinessText.installRestricted(spacePolicy.reason),
                errorCode = InstallFailureCode.POLICY_BLOCKED.name)
            return
        }

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
                targetVersionCode = detail.versionCode,
                signerCertificateSha256 = detail.signerCertificateSha256.toSet(),
            ),
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
                    // 系统成功事实先可见，镜像或清理失败不能将真实安装结果反写成失败。
                    stateCenter.updateInstall(appId, InstallStatus.INSTALLED, versionName = event.installedVersion, versionCode = event.installedVersionCode)
                    try {
                        repository.markInstalled(appId)
                    } catch (canceled: CancellationException) {
                        throw canceled
                    } catch (failure: Exception) {
                        logger.w("InstallManager", "Unable to persist installed mirror: $appId", failure)
                    }
                    try {
                        repository.removeDownloadTask(appId)
                        repository.clearDownloadedApk(appId)
                    } catch (canceled: CancellationException) {
                        throw canceled
                    } catch (failure: Exception) {
                        logger.w("InstallManager", "Unable to clear installed APK: $appId", failure)
                    }
                    stateCenter.updateDownload(appId, DownloadStatus.IDLE, progress = 0, localApkPath = null)
                    tracker.track("install_success_$appId")
                }

                is InstallEvent.Failed -> {
                    // APK 缺失或损坏时，需要同时把下载状态打回失败，提示用户重新下载。
                    if (invalidatesDownloadedApk(event.code)) {
                        invalidateDownloadedApk(appId, event.code, event.message)
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

    /** 先持久化失效事实，再回收文件，防止重启继续把不可信产物当作已完成任务。 */
    private suspend fun invalidateDownloadedApk(appId: String, code: InstallFailureCode, message: String) {
        val reason = BusinessText.retryDownload(message)
        stateCenter.updateDownload(appId, DownloadStatus.FAILED, progress = 0, localApkPath = null,
            errorMessage = reason, errorCode = code.name)
        repository.getDownloadTask(appId)?.let { record ->
            repository.saveDownloadTask(record.copy(status = DownloadStatus.FAILED, progress = 0,
                downloadedBytes = 0L, speedBytesPerSec = 0L, failureCode = code.name, failureMessage = reason,
                eTag = null, lastModified = null, updatedAt = System.currentTimeMillis()))
        }
        repository.saveDownloadSegments(appId, emptyList())
        repository.clearDownloadedApk(appId)
    }

    /** 判断失败是否说明当前下载产物不再可信、不能继续复用。 */
    private fun invalidatesDownloadedApk(code: InstallFailureCode): Boolean {
        return when (code) {
            InstallFailureCode.APK_MISSING,
            InstallFailureCode.APK_INVALID,
            InstallFailureCode.APK_PACKAGE_MISMATCH,
            InstallFailureCode.APK_VERSION_MISSING,
            InstallFailureCode.APK_VERSION_MISMATCH,
            InstallFailureCode.APK_SIGNER_MISSING,
            InstallFailureCode.APK_SIGNER_MISMATCH -> true

            else -> false
        }
    }

    /** 清理指定应用的安装失败态，并恢复到可继续操作的状态。 */
    override suspend fun clearFailed(appId: String) {
        require(appId.isNotBlank()) { "appId 不能为空" }
        artifactAccess.tryUse(appId) { clearFailedInternal(appId) }
    }

    private suspend fun clearFailedInternal(appId: String) {
        val snapshot = stateCenter.snapshot(appId)
        if (snapshot.installStatus != InstallStatus.FAILED) return
        val apkPath = repository.getDownloadedApk(appId)
        val apkFile = apkPath?.let { File(it) }
        val hasValidApk = apkFile?.isFile == true && apkFile.canRead() && apkFile.length() > 0 &&
            snapshot.downloadStatus != DownloadStatus.FAILED
        val idleInstallStatus = if (snapshot.installedVersion != null) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED
        if (hasValidApk) {
            // 本地 APK 仍然可用时，保留下载完成态，让用户可以直接重新安装。
            stateCenter.updateDownload(appId, DownloadStatus.COMPLETED, progress = 100, localApkPath = apkPath, errorMessage = null, errorCode = null)
            stateCenter.updateInstall(appId, idleInstallStatus, errorMessage = null, errorCode = null)
        } else {
            // 本地 APK 已经失效时，直接把下载态和安装态都复位，避免误导用户。
            repository.clearDownloadedApk(appId)
            repository.saveDownloadSegments(appId, emptyList())
            repository.removeDownloadTask(appId)
            stateCenter.updateInstall(appId, idleInstallStatus, errorMessage = null, errorCode = null)
            stateCenter.updateDownload(appId, DownloadStatus.IDLE, progress = 0, localApkPath = null, errorMessage = null, errorCode = null)
        }
        // 最后统一清空错误展示，保证页面从失败态中退出来。
        stateCenter.resetError(appId)
    }

    private companion object {
        private const val PLATFORM_UNSUPPORTED_ERROR_CODE = "PLATFORM_UNSUPPORTED"
    }
}
