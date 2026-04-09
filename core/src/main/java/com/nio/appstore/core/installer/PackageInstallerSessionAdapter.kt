package com.nio.appstore.core.installer

import android.content.Context
import java.io.File

data class InstallCommitResult(
    /** 平台安装会话提交是否成功。 */
    val success: Boolean,
    /** 展示给用户的提交结果详情。 */
    val message: String,
    /** 平台可返回时给出的已安装应用包名。 */
    val installedPackageName: String? = null,
)

interface PackageInstallerSessionAdapter {
    fun createSession(request: InstallRequest): Int
    fun writeApkToSession(sessionId: Int, apkFile: File): Boolean
    fun commitSession(sessionId: Int): InstallCommitResult
    fun supportsRealSession(): Boolean
}

class SystemPackageInstallerSessionAdapter(context: Context) : PackageInstallerSessionAdapter {

    private val appContext = context.applicationContext

    override fun createSession(request: InstallRequest): Int {
        return (System.currentTimeMillis() % 100000).toInt()
    }

    override fun writeApkToSession(sessionId: Int, apkFile: File): Boolean {
        return apkFile.exists() && apkFile.length() > 0L
    }

    override fun commitSession(sessionId: Int): InstallCommitResult {
        return InstallCommitResult(
            success = true,
            message = InstallerText.SESSION_COMMIT_SUCCESS,
            installedPackageName = null,
        )
    }

    override fun supportsRealSession(): Boolean {
        // 当前先保留骨架实现。
        // 后续可在这里检查系统安装器可用性、权限或厂商能力。
        return false
    }
}
