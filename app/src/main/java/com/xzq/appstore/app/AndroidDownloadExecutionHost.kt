package com.xzq.appstore.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.xzq.appstore.R
import com.xzq.appstore.domain.download.DownloadExecutionHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 主线程串行管理服务和下载租约；服务确认进入前台后才允许执行下载。 */
class AndroidDownloadExecutionHost(private val context: Context) : DownloadExecutionHost {
    private val active = MutableStateFlow<Set<String>>(emptySet())
    val activeAppIds = active.asStateFlow()
    private var serviceToken: Any? = null
    private var starting = false
    private var recovering = false
    private var started = CompletableDeferred<Unit>()

    override suspend fun acquire(appId: String) = withContext(Dispatchers.Main.immediate) {
        active.value = active.value + appId
        try {
            if (serviceToken == null && !starting) {
                started = CompletableDeferred()
                starting = true
                ContextCompat.startForegroundService(context, Intent(context, DownloadForegroundService::class.java))
            }
            withTimeout(10_000L) { started.await() }
        } catch (failure: Exception) {
            withContext(NonCancellable) { release(appId) }
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            throw IllegalStateException(context.getString(R.string.download_service_unavailable), failure)
        }
    }

    override suspend fun release(appId: String) = withContext(Dispatchers.Main.immediate) {
        active.value = active.value - appId
        stopIfIdle()
    }

    fun onServiceStarted(token: Any) {
        serviceToken = token
        recovering = true
        starting = false
        started.complete(Unit)
    }

    fun onServiceStartFailed(failure: Exception) {
        starting = false
        started.completeExceptionally(failure)
    }

    fun finishRecovery(token: Any) {
        if (serviceToken !== token) return
        recovering = false
        stopIfIdle()
    }

    /** 只接受当前服务实例的停止回调，旧实例不能终止新一轮下载。 */
    fun onServiceStopped(token: Any): Boolean {
        if (serviceToken !== token) return false
        serviceToken = null
        recovering = false
        starting = false
        return active.value.isNotEmpty()
    }

    private fun stopIfIdle() {
        if (active.value.isNotEmpty() || recovering) return
        serviceToken = null
        starting = false
        context.stopService(Intent(context, DownloadForegroundService::class.java))
    }
}
