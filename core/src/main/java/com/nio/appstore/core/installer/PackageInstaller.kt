package com.nio.appstore.core.installer

import android.content.Intent
import java.io.File

interface PackageInstaller {
    /** 执行一次安装请求，并通过事件回调持续上报安装进展。 */
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
    /** 安装前发现 APK 文件不存在。 */
    APK_MISSING(InstallerText.FAILURE_APK_MISSING),
    /** APK 文件无效或不可解析。 */
    APK_INVALID(InstallerText.FAILURE_APK_INVALID),
    /** 安装前被策略中心拦截。 */
    POLICY_BLOCKED(InstallerText.FAILURE_POLICY_BLOCKED),
    /** 创建系统安装会话失败。 */
    SESSION_CREATE_FAILED(InstallerText.FAILURE_SESSION_CREATE_FAILED),
    /** APK 写入系统安装会话失败。 */
    SESSION_WRITE_FAILED(InstallerText.FAILURE_SESSION_WRITE_FAILED),
    /** 提交系统安装会话失败。 */
    SESSION_COMMIT_FAILED(InstallerText.FAILURE_SESSION_COMMIT_FAILED),
    /** 安装过程中发生中断。 */
    INSTALL_INTERRUPTED(InstallerText.FAILURE_INSTALL_INTERRUPTED),
    /** 未归类的未知安装失败。 */
    UNKNOWN(InstallerText.FAILURE_UNKNOWN),
}

sealed class InstallEvent {
    /** 安装任务已进入等待执行阶段。 */
    object Waiting : InstallEvent()
    /** 系统安装会话已创建。 */
    data class SessionCreated(val sessionId: Int) : InstallEvent()
    /** 安装进度发生变化。 */
    data class Progress(val sessionId: Int, val progress: Int) : InstallEvent()
    data class PendingUserAction(
        /** 当前等待系统确认的安装会话标识。 */
        val sessionId: Int,
        /** 系统确认阶段展示给用户的提示文案。 */
        val message: String,
        /** 系统要求拉起的确认 Intent。 */
        val confirmationIntent: Intent? = null,
    ) : InstallEvent()
    /** 系统已经开始真正执行安装。 */
    object Installing : InstallEvent()
    /** 安装成功并返回最终版本号。 */
    data class Success(val installedVersion: String) : InstallEvent()
    /** 安装失败并返回归一化失败信息。 */
    data class Failed(val code: InstallFailureCode, val message: String) : InstallEvent()
}
