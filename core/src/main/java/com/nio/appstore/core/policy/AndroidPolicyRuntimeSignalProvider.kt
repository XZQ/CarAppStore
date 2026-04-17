package com.nio.appstore.core.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.nio.appstore.core.logger.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AndroidPolicyRuntimeSignalProvider 提供 Android 侧可直接获取的实时策略信号。
 *
 * 当前已接入：
 * 1. Wi‑Fi 网络状态
 * 2. 低存储状态
 * 3. 驻车状态默认兜底为 true，等待 OEM 车况接入替换
 */
class AndroidPolicyRuntimeSignalProvider(
    context: Context,
    /** 统一日志入口。 */
    private val logger: AppLogger = AppLogger(),
) : PolicyRuntimeSignalProvider {
    /** 应用级上下文。 */
    private val appContext = context.applicationContext

    /** 当前实时策略信号流。 */
    private val signalsFlow = MutableStateFlow(readSignals())

    /** 网络能力监听器，用于实时刷新 Wi‑Fi 状态。 */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshSignals()

        override fun onLost(network: Network) = refreshSignals()

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshSignals()
        }
    }

    /** 存储状态广播接收器，用于实时刷新低存储标记。 */
    @Suppress("DEPRECATION")
    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_DEVICE_STORAGE_LOW,
                Intent.ACTION_DEVICE_STORAGE_OK,
                -> refreshSignals()
            }
        }
    }

    init {
        registerObservers()
    }

    /** 观察实时策略信号。 */
    override fun observeSignals(): StateFlow<PolicyRuntimeSignals> = signalsFlow

    /** 读取当前实时策略信号。 */
    override fun currentSignals(): PolicyRuntimeSignals = signalsFlow.value

    /** 注册系统监听。 */
    @Suppress("DEPRECATION")
    private fun registerObservers() {
        runCatching {
            val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }.onFailure { logger.d(TAG, "register network callback failed: ${it.message}") }

        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                addAction(Intent.ACTION_DEVICE_STORAGE_OK)
            }
            appContext.registerReceiver(storageReceiver, filter)
        }.onFailure { logger.d(TAG, "register storage receiver failed: ${it.message}") }
    }

    /** 刷新当前实时信号。 */
    private fun refreshSignals() {
        signalsFlow.value = readSignals()
    }

    /** 读取当前系统实时信号。 */
    private fun readSignals(): PolicyRuntimeSignals {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        return PolicyRuntimeSignals(
            wifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            parkingMode = true,
            lowStorageMode = appContext.filesDir.usableSpace < MIN_REQUIRED_SPACE_BYTES,
        )
    }

    private companion object {
        private const val TAG = "AndroidPolicyRuntimeSignalProvider"
        private const val MIN_REQUIRED_SPACE_BYTES = 8L * 1024L * 1024L
    }
}
