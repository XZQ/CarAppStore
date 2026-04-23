package com.nio.appstore.core.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import com.nio.appstore.core.logger.AppLogger
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
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
    /** 创建一个平台安装会话，并返回 sessionId。 */
    fun createSession(request: InstallRequest): Int
    /** 将 APK 文件写入指定安装会话。 */
    fun writeApkToSession(sessionId: Int, apkFile: File): Boolean
    /** 提交安装会话，并等待平台返回最终结果。 */
    suspend fun commitSession(
        sessionId: Int,
        onPendingUserAction: suspend (message: String, confirmationIntent: Intent) -> Unit,
    ): InstallCommitResult
    /** 当前运行环境是否支持真实系统安装会话。 */
    fun supportsRealSession(): Boolean
}

class SystemPackageInstallerSessionAdapter(
    context: Context,
    /** 系统要求用户确认时，用于把 intent 派发给壳层。 */
    private val installUserActionDispatcher: InstallUserActionDispatcher,
    /** 安装会话诊断日志入口。 */
    private val logger: AppLogger = AppLogger(),
) : PackageInstallerSessionAdapter {

    /** 应用级上下文，避免广播注册和安装调用依赖页面生命周期。 */
    private val appContext = context.applicationContext
    /** 系统提供的安装会话入口。 */
    private val systemPackageInstaller = appContext.packageManager.packageInstaller

    /** 创建平台安装会话，并预置包名与 APK 大小信息。 */
    override fun createSession(request: InstallRequest): Int {
        if (!supportsRealSession()) return -1
        return runCatching {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(request.packageName)
                setSize(request.apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 首次安装必须走系统确认页，避免平台因请求免确认而直接拒绝会话提交。
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            systemPackageInstaller.createSession(params)
        }.getOrDefault(-1)
    }

    /** 将本地 APK 内容写入到指定平台安装会话。 */
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

    /** 提交平台安装会话，并按阶段等待系统回调。 */
    override suspend fun commitSession(
        sessionId: Int,
        onPendingUserAction: suspend (message: String, confirmationIntent: Intent) -> Unit,
    ): InstallCommitResult {
        if (!supportsRealSession()) {
            return InstallCommitResult(
                success = false,
                message = InstallerText.SESSION_NOT_SUPPORTED,
                installedPackageName = null,
            )
        }
        val resultAction = ACTION_INSTALL_COMMIT_PREFIX + sessionId + "." + System.currentTimeMillis()
        /** 安装提交广播结果队列，用于串行消费系统返回的多阶段结果。 */
        val resultQueue = LinkedBlockingQueue<CommitCallbackPayload>()
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    ?: PackageInstaller.STATUS_FAILURE
                val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.ifBlank { null }
                    ?: defaultStatusMessage(status)
                logger.d(TAG, LOG_COMMIT_CALLBACK_FORMAT.format(status, message))
                resultQueue.offer(
                    CommitCallbackPayload(
                        status = status,
                        message = message,
                        confirmationIntent = readConfirmationIntent(intent),
                        installedPackageName = intent?.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
                    )
                )
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            resultReceiver,
            IntentFilter(resultAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return try {
            // 先提交安装会话，再阻塞等待系统通过广播返回结果。
            val callbackIntent = Intent(resultAction).setPackage(appContext.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                callbackIntent,
                buildCommitPendingIntentFlags(),
            )
            val session = systemPackageInstaller.openSession(sessionId)
            try {
                session.commit(pendingIntent.intentSender)
            } finally {
                session.close()
            }
            waitForCommitResult(resultQueue, onPendingUserAction)
        } catch (t: Throwable) {
            val message = buildCommitThrowableMessage(t)
            logger.d(TAG, message)
            InstallCommitResult(
                success = false,
                message = message,
                installedPackageName = null,
            )
        } finally {
            runCatching { appContext.unregisterReceiver(resultReceiver) }
        }
    }

    /** 判断当前设备是否满足真实安装会话能力要求。 */
    override fun supportsRealSession(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appContext.packageManager.canRequestPackageInstalls()
    }

    /** 为平台状态码补齐默认展示文案。 */
    private fun defaultStatusMessage(status: Int): String {
        return when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallerText.SESSION_PENDING_USER_ACTION
            else -> InstallerText.sessionCommitFailureWithStatus(status)
        }
    }

    /** 将提交阶段异常转换为可持久化的失败详情。 */
    private fun buildCommitThrowableMessage(throwable: Throwable): String {
        val detail = throwable.message
            ?.ifBlank { null }
            ?: throwable.javaClass.simpleName
        return InstallerText.sessionCommitFailureWithDetail(detail)
    }

    /** 构建安装回调 PendingIntent 标记，Android S+ 需要可变回调承载平台填充的状态。 */
    private fun buildCommitPendingIntentFlags(): Int {
        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            NO_ADDITIONAL_PENDING_INTENT_FLAGS
        }
        return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    }

    /** 从平台广播中解析出系统确认 intent。 */
    private fun readConfirmationIntent(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    /** 串行消费平台回调，直到拿到最终提交结果。 */
    private suspend fun waitForCommitResult(
        resultQueue: LinkedBlockingQueue<CommitCallbackPayload>,
        onPendingUserAction: suspend (message: String, confirmationIntent: Intent) -> Unit,
    ): InstallCommitResult {
        /** 是否已经进入系统确认阶段，进入后需要延长最终结果等待时间。 */
        var pendingUserActionObserved = false
        while (true) {
            val timeoutSeconds = if (pendingUserActionObserved) {
                FINAL_RESULT_TIMEOUT_SECONDS
            } else {
                INITIAL_RESULT_TIMEOUT_SECONDS
            }
            val callback = resultQueue.poll(timeoutSeconds, TimeUnit.SECONDS)
                ?: return InstallCommitResult(
                    success = false,
                    message = InstallerText.SESSION_COMMIT_TIMEOUT,
                    installedPackageName = null,
                )
            when (callback.status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmationIntent = callback.confirmationIntent
                    if (confirmationIntent == null) {
                        return InstallCommitResult(
                            success = false,
                            message = InstallerText.SESSION_PENDING_USER_ACTION_MISSING_INTENT,
                            installedPackageName = callback.installedPackageName,
                        )
                    }
                    if (!pendingUserActionObserved) {
                        // 首次进入系统确认阶段时，同时通知壳层拉起确认页和业务层更新状态。
                        pendingUserActionObserved = true
                        installUserActionDispatcher.dispatch(confirmationIntent)
                        onPendingUserAction(InstallerText.SESSION_PENDING_USER_ACTION, confirmationIntent)
                    }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    return InstallCommitResult(
                        success = true,
                        message = InstallerText.SESSION_COMMIT_SUCCESS,
                        installedPackageName = callback.installedPackageName,
                    )
                }

                else -> {
                    return InstallCommitResult(
                        success = false,
                        message = callback.message,
                        installedPackageName = callback.installedPackageName,
                    )
                }
            }
        }
    }

    private data class CommitCallbackPayload(
        /** 系统安装会话回调状态码。 */
        val status: Int,
        /** 系统返回的原始状态信息。 */
        val message: String,
        /** 系统安装确认页入口。 */
        val confirmationIntent: Intent?,
        /** 平台可返回时给出的已安装包名。 */
        val installedPackageName: String?,
    )

    private companion object {
        /** 安装会话诊断日志标签。 */
        const val TAG = "PkgSessionAdapter"

        /** 平台提交回调诊断日志格式。 */
        const val LOG_COMMIT_CALLBACK_FORMAT = "install commit callback status=%d message=%s"

        /** 低版本无需额外 PendingIntent 可变性标记。 */
        const val NO_ADDITIONAL_PENDING_INTENT_FLAGS = 0

        /** 安装会话写入时使用的基础安装包条目名。 */
        const val APK_ENTRY_NAME = "base.apk"

        /** 安装提交结果广播 action 前缀。 */
        const val ACTION_INSTALL_COMMIT_PREFIX = "com.nio.appstore.INSTALL_COMMIT."

        /** 首次等待系统提交结果回调的最长秒数。 */
        const val INITIAL_RESULT_TIMEOUT_SECONDS = 30L

        /** 用户确认后等待最终安装结果回调的最长秒数。 */
        const val FINAL_RESULT_TIMEOUT_SECONDS = 300L
    }
}
