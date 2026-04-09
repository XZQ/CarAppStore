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
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
    private val policyCenter: PolicyCenter,
    private val packageInstaller: PackageInstaller,
    private val logger: AppLogger,
    private val tracker: EventTracker,
) : InstallManager {

    override suspend fun install(appId: String) {
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

        val detail = repository.getAppDetail(appId)
        val targetVersion = repository.peekStagedUpgradeVersion(appId) ?: detail.versionName
        val apkFile = File(apkPath)

        logger.d("InstallManager", "install: $appId, apkPath=$apkPath, size=${apkFile.length()}")
        tracker.track("install_start_$appId")
        stateCenter.resetError(appId)

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
                    stateCenter.updateInstall(appId, InstallStatus.WAITING)
                }
                is InstallEvent.SessionCreated -> {
                    logger.d("InstallManager", "session created: ${event.sessionId} for $appId")
                    stateCenter.updateInstall(appId, InstallStatus.WAITING)
                }
                InstallEvent.Installing -> {
                    stateCenter.updateInstall(appId, InstallStatus.INSTALLING)
                }
                is InstallEvent.Progress -> {
                    stateCenter.updateInstall(appId, InstallStatus.INSTALLING)
                    logger.d("InstallManager", "install progress: $appId -> ${event.progress}%")
                }
                is InstallEvent.Success -> {
                    repository.markInstalled(appId)
                    repository.removeDownloadTask(appId)
                    stateCenter.updateInstall(appId, InstallStatus.INSTALLED, versionName = event.installedVersion)
                    stateCenter.updateDownload(appId, DownloadStatus.COMPLETED, progress = 100, localApkPath = apkPath)
                    tracker.track("install_success_$appId")
                }
                is InstallEvent.Failed -> {
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

    override suspend fun clearFailed(appId: String) {
        val apkPath = repository.getDownloadedApk(appId)
        val apkFile = apkPath?.let { File(it) }
        val hasValidApk = apkFile?.exists() == true && apkFile.length() > 0
        if (hasValidApk) {
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
        stateCenter.resetError(appId)
    }
}
