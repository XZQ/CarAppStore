package com.nio.appstore.core.installer

import java.io.File

interface PackageInstaller {
    suspend fun install(
        request: InstallRequest,
        onEvent: suspend (InstallEvent) -> Unit,
    )
}

data class InstallRequest(
    val appId: String,
    val packageName: String,
    val targetVersion: String,
    val apkFile: File,
)

enum class InstallFailureCode(val displayText: String) {
    APK_MISSING("安装包不存在"),
    APK_INVALID("安装包无效"),
    POLICY_BLOCKED("安装受限"),
    SESSION_CREATE_FAILED("安装会话创建失败"),
    SESSION_WRITE_FAILED("安装包写入失败"),
    SESSION_COMMIT_FAILED("安装会话提交失败"),
    INSTALL_INTERRUPTED("安装中断"),
    UNKNOWN("未知安装错误"),
}

sealed class InstallEvent {
    object Waiting : InstallEvent()
    data class SessionCreated(val sessionId: Int) : InstallEvent()
    data class Progress(val sessionId: Int, val progress: Int) : InstallEvent()
    object Installing : InstallEvent()
    data class Success(val installedVersion: String) : InstallEvent()
    data class Failed(val code: InstallFailureCode, val message: String) : InstallEvent()
}
