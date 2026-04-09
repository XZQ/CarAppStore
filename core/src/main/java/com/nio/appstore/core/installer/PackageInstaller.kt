package com.nio.appstore.core.installer

import java.io.File

interface PackageInstaller {
    suspend fun install(
        request: InstallRequest,
        onEvent: suspend (InstallEvent) -> Unit,
    )
}

data class InstallRequest(
    /** 安装链路对应的稳定应用标识。 */
    val appId: String,
    /** 安装完成后期望得到的包名。 */
    val packageName: String,
    /** 成功后应写入为已安装的目标版本。 */
    val targetVersion: String,
    /** 当前用于安装的本地安装包文件。 */
    val apkFile: File,
)

enum class InstallFailureCode(val displayText: String) {
    APK_MISSING(InstallerText.FAILURE_APK_MISSING),
    APK_INVALID(InstallerText.FAILURE_APK_INVALID),
    POLICY_BLOCKED(InstallerText.FAILURE_POLICY_BLOCKED),
    SESSION_CREATE_FAILED(InstallerText.FAILURE_SESSION_CREATE_FAILED),
    SESSION_WRITE_FAILED(InstallerText.FAILURE_SESSION_WRITE_FAILED),
    SESSION_COMMIT_FAILED(InstallerText.FAILURE_SESSION_COMMIT_FAILED),
    INSTALL_INTERRUPTED(InstallerText.FAILURE_INSTALL_INTERRUPTED),
    UNKNOWN(InstallerText.FAILURE_UNKNOWN),
}

sealed class InstallEvent {
    object Waiting : InstallEvent()
    data class SessionCreated(val sessionId: Int) : InstallEvent()
    data class Progress(val sessionId: Int, val progress: Int) : InstallEvent()
    object Installing : InstallEvent()
    data class Success(val installedVersion: String) : InstallEvent()
    data class Failed(val code: InstallFailureCode, val message: String) : InstallEvent()
}
