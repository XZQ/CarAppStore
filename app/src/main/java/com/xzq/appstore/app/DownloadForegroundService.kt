package com.xzq.appstore.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xzq.appstore.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 用户发起下载的 Android 执行宿主；服务立即发通知，业务继续由下载模块负责。 */
class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var container: AppContainer
    private lateinit var host: AndroidDownloadExecutionHost
    private var initializationJob: Job? = null
    private var foregroundReady = false

    override fun onCreate() {
        super.onCreate()
        container = (application as App).appContainer
        host = container.downloadExecutionHost
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW))
        try {
            val notification = buildNotification(0, 0)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION_ID, notification)
            host.onServiceStarted(this)
            foregroundReady = true
        } catch (failure: Exception) {
            container.logger.w("DownloadService", "Unable to enter foreground", failure)
            host.onServiceStartFailed(failure)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) return START_NOT_STICKY
        if (intent?.action == ACTION_PAUSE) {
            scope.launch {
                try {
                    container.awaitReady()
                    container.downloadManager.pauseAllDownloads()
                } catch (canceled: CancellationException) {
                    throw canceled
                } catch (failure: Exception) {
                    container.logger.w("DownloadService", "Unable to pause downloads", failure)
                } finally {
                    stopSelf()
                }
            }
        } else if (initializationJob == null) {
            initializationJob = scope.launch {
                try {
                    container.awaitReady()
                    if (intent == null) container.downloadManager.resumeInterruptedDownloads()
                    observeProgress()
                } catch (canceled: CancellationException) {
                    throw canceled
                } catch (failure: Exception) {
                    container.logger.w("DownloadService", "Unable to recover downloads", failure)
                    stopSelf()
                } finally {
                    withContext(Dispatchers.Main) { host.finishRecovery(this@DownloadForegroundService) }
                }
            }
        }
        return START_STICKY
    }

    private fun observeProgress() {
        scope.launch {
            combine(host.activeAppIds, container.stateCenter.observeAll()) { ids, states ->
                ids.size to ids.map { states[it]?.progress ?: 0 }.let { if (it.isEmpty()) 0 else it.average().toInt() }
            }.collect { (count, progress) ->
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@DownloadForegroundService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(count, progress))
                }
                delay(1000L)
            }
        }
    }

    internal fun buildNotification(count: Int, progress: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1, Intent(this, DownloadForegroundService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(if (count == 0) getString(R.string.download_notification_preparing) else getString(R.string.download_notification_count, count))
            .setProgress(100, progress, count == 0)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.download_notification_pause), pause)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        container.pauseDownloadsAfterServiceStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (::host.isInitialized && host.onServiceStopped(this)) container.pauseDownloadsAfterServiceStop()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
        internal const val ACTION_PAUSE = "com.xzq.appstore.action.PAUSE_DOWNLOADS"
        private const val CHANNEL_ID = "app_downloads"
        private const val NOTIFICATION_ID = 1001
    }
}
