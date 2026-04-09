package com.nio.appstore.core.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    /** 系统提供的安装会话入口。 */
    private val systemPackageInstaller = appContext.packageManager.packageInstaller

    override fun createSession(request: InstallRequest): Int {
        if (!supportsRealSession()) return -1
        return runCatching {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(request.packageName)
                setSize(request.apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            systemPackageInstaller.createSession(params)
        }.getOrDefault(-1)
    }

    override fun writeApkToSession(sessionId: Int, apkFile: File): Boolean {
        if (!supportsRealSession()) return false
        return runCatching {
            val session = systemPackageInstaller.openSession(sessionId)
            try {
                apkFile.inputStream().use { input ->
                    session.openWrite(APK_ENTRY_NAME, 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            } finally {
                session.close()
            }
            true
        }.getOrDefault(false)
    }

    override fun commitSession(sessionId: Int): InstallCommitResult {
        if (!supportsRealSession()) {
            return InstallCommitResult(
                success = false,
                message = InstallerText.SESSION_NOT_SUPPORTED,
                installedPackageName = null,
            )
        }
        val resultAction = ACTION_INSTALL_COMMIT_PREFIX + sessionId + "." + System.currentTimeMillis()
        val awaitLatch = CountDownLatch(1)
        var resultStatus = PackageInstaller.STATUS_FAILURE
        var resultMessage = InstallerText.FAILURE_SESSION_COMMIT_FAILED
        var installedPackageName: String? = null
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                resultStatus = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    ?: PackageInstaller.STATUS_FAILURE
                resultMessage = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.ifBlank { null }
                    ?: defaultStatusMessage(resultStatus)
                installedPackageName = intent?.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                awaitLatch.countDown()
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            resultReceiver,
            IntentFilter(resultAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return try {
            val callbackIntent = Intent(resultAction).setPackage(appContext.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val session = systemPackageInstaller.openSession(sessionId)
            try {
                session.commit(pendingIntent.intentSender)
            } finally {
                session.close()
            }
            if (!awaitLatch.await(COMMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return InstallCommitResult(
                    success = false,
                    message = InstallerText.SESSION_COMMIT_TIMEOUT,
                    installedPackageName = installedPackageName,
                )
            }
            InstallCommitResult(
                success = resultStatus == PackageInstaller.STATUS_SUCCESS,
                message = when (resultStatus) {
                    PackageInstaller.STATUS_SUCCESS -> InstallerText.SESSION_COMMIT_SUCCESS
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallerText.SESSION_PENDING_USER_ACTION
                    else -> resultMessage
                },
                installedPackageName = installedPackageName,
            )
        } catch (_: Throwable) {
            InstallCommitResult(
                success = false,
                message = InstallerText.FAILURE_SESSION_COMMIT_FAILED,
                installedPackageName = null,
            )
        } finally {
            runCatching { appContext.unregisterReceiver(resultReceiver) }
        }
    }

    override fun supportsRealSession(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appContext.packageManager.canRequestPackageInstalls()
    }

    private fun defaultStatusMessage(status: Int): String {
        return when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallerText.SESSION_PENDING_USER_ACTION
            else -> InstallerText.FAILURE_SESSION_COMMIT_FAILED
        }
    }

    private companion object {
        /** 安装会话写入时使用的基础安装包条目名。 */
        const val APK_ENTRY_NAME = "base.apk"

        /** 安装提交结果广播 action 前缀。 */
        const val ACTION_INSTALL_COMMIT_PREFIX = "com.nio.appstore.INSTALL_COMMIT."

        /** 等待系统安装结果回调的最长秒数。 */
        const val COMMIT_TIMEOUT_SECONDS = 30L
    }
}
