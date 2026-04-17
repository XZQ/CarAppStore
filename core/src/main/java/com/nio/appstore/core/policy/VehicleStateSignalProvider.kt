package com.nio.appstore.core.policy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * VehicleRuntimeState 描述来自 OEM 或车机平台的实时车况信号。
 */
data class VehicleRuntimeState(
    /** 当前车辆是否处于驻车状态。 */
    val parkingMode: Boolean = true,
    /** 当前车况信号来源名称。 */
    val sourceName: String = "fallback",
)

/**
 * VehicleStateSignalProvider 定义 OEM 车况信号提供者。
 */
interface VehicleStateSignalProvider {
    /** 观察实时车况信号。 */
    fun observeVehicleState(): StateFlow<VehicleRuntimeState>

    /** 读取当前车况信号快照。 */
    fun currentVehicleState(): VehicleRuntimeState
}

/**
 * StaticVehicleStateSignalProvider 提供默认的驻车兜底信号。
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
