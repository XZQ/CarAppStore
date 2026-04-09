package com.nio.appstore.core.installer

import android.content.Context
import java.io.File

data class InstallCommitResult(
    val success: Boolean,
    val message: String,
    val installedPackageName: String? = null,
)

interface PackageInstallerSessionAdapter {
    fun createSession(request: InstallRequest): Int
    fun writeApkToSession(sessionId: Int, apkFile: File): Boolean
    fun commitSession(sessionId: Int): InstallCommitResult
    fun supportsRealSession(): Boolean
}

class SystemPackageInstallerSessionAdapter(
    context: Context,
) : PackageInstallerSessionAdapter {

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
            message = "安装会话提交成功",
            installedPackageName = null,
        )
    }

    override fun supportsRealSession(): Boolean {
        // R2-3 skeleton:
        // future: check PackageInstaller availability / permission / OEM capability.
        return false
    }
}
