package com.xzq.appstore.core.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * VehicleRuntimeState 描述来自 OEM 或车机平台的实时车况信号。
 */
data class VehicleRuntimeState(
    /** 当前车辆是否处于驻车状态。 */
    val parkingMode: Boolean = false,
    /** 当前车况信号来源名称。 */
    val sourceName: String = "fallback",
    val powerOn: Boolean? = null,
)

/**
 * VehicleStateSignalProvider 定义 OEM 车况信号提供者。
 *
 * 实现类若注册了系统监听（广播、回调等），应在 [close] 中反注册，避免进程级泄漏。
 */
interface VehicleStateSignalProvider {
    /** 观察实时车况信号。 */
    fun observeVehicleState(): StateFlow<VehicleRuntimeState>

    /** 读取当前车况信号快照。 */
    fun currentVehicleState(): VehicleRuntimeState

    /** 释放底层资源。默认空实现以兼容纯内存 provider。 */
    fun close() {}
}

class BroadcastVehicleStateSignalProvider(
    context: Context,
    private val action: String,
    private val parkingExtraName: String,
    private val powerExtraName: String = "",
    initialState: VehicleRuntimeState = VehicleRuntimeState(sourceName = "broadcast"),
) : VehicleStateSignalProvider {
    private val appContext = context.applicationContext
    private val stateFlow = MutableStateFlow(initialState)

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != action) return
                stateFlow.value =
                    VehicleRuntimeState(
                        parkingMode =
                            VehicleSignalValueParser.booleanExtra(
                                intent.extras?.get(parkingExtraName),
                                stateFlow.value.parkingMode,
                            ),
                        sourceName = "broadcast:$action",
                        powerOn = optionalBooleanExtra(intent, powerExtraName, stateFlow.value.powerOn),
                    )
            }
        }

    init {
        require(action.isNotBlank()) { "vehicle broadcast action must not be blank" }
        require(parkingExtraName.isNotBlank()) { "parking extra name must not be blank" }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    override fun observeVehicleState(): StateFlow<VehicleRuntimeState> = stateFlow

    override fun currentVehicleState(): VehicleRuntimeState = stateFlow.value

    /** 反注册广播接收器，避免 Context 复用场景（如测试）下累积悬挂 receiver。 */
    override fun close() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun optionalBooleanExtra(
        intent: Intent,
        name: String,
        fallback: Boolean?,
    ): Boolean? {
        if (name.isBlank() || !intent.hasExtra(name)) return fallback
        return VehicleSignalValueParser.booleanExtra(intent.extras?.get(name), fallback ?: false)
    }
}

object VehicleSignalValueParser {
    fun booleanExtra(
        value: Any?,
        fallback: Boolean,
    ): Boolean =
        when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> stringToBoolean(value) ?: fallback
            else -> fallback
        }

    private fun stringToBoolean(value: String): Boolean? =
        when (value.trim().lowercase()) {
            "true", "1", "yes", "y", "on", "park", "parked", "parking", "p" -> true
            "false", "0", "no", "n", "off", "drive", "driving", "d", "moving" -> false
            else -> null
        }
}

/**
 * StaticVehicleStateSignalProvider 提供默认的安全兜底信号。
 */
class StaticVehicleStateSignalProvider(
    /** 当前固定输出的车况快照。 */
    initialState: VehicleRuntimeState = VehicleRuntimeState(),
) : VehicleStateSignalProvider {
    /** 当前车况状态流。 */
    private val stateFlow = MutableStateFlow(initialState)

    /** 观察实时车况信号。 */
    override fun observeVehicleState(): StateFlow<VehicleRuntimeState> = stateFlow

    /** 读取当前车况信号快照。 */
    override fun currentVehicleState(): VehicleRuntimeState = stateFlow.value
}
