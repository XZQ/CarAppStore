package com.xzq.appstore.domain.policy

import com.xzq.appstore.core.policy.PolicyRuntimeSignals
import com.xzq.appstore.data.model.PolicySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicySettingsMergerTest {

    @Test
    fun `mergePolicySettings 会把实时网络限制合并到生效策略`() {
        val result = mergePolicySettings(
            stored = PolicySettings(wifiConnected = true, parkingMode = true, lowStorageMode = false),
            runtime = PolicyRuntimeSignals(wifiConnected = false, parkingMode = true, lowStorageMode = false),
        )

        assertEquals(PolicySettings(wifiConnected = false, parkingMode = true, lowStorageMode = false), result)
    }

    @Test
    fun `mergePolicySettings 通用平台忽略无意义的非驻车信号`() {
        val result = mergePolicySettings(
            stored = PolicySettings(wifiConnected = true, parkingMode = true, lowStorageMode = false),
            runtime = PolicyRuntimeSignals(wifiConnected = true, parkingMode = false, lowStorageMode = false),
        )

        assertEquals(PolicySettings(wifiConnected = true, parkingMode = true, lowStorageMode = false), result)
    }

    @Test
    fun `mergePolicySettings 车载平台合并实时驻车限制`() {
        val result = mergePolicySettings(
            stored = PolicySettings(wifiConnected = true, parkingMode = true, lowStorageMode = false),
            runtime = PolicyRuntimeSignals(
                wifiConnected = true,
                parkingMode = false,
                lowStorageMode = false,
                vehicleInstallPolicyEnabled = true,
            ),
        )

        assertEquals(
            PolicySettings(
                wifiConnected = true,
                parkingMode = false,
                lowStorageMode = false,
                vehicleInstallPolicyEnabled = true,
            ),
            result,
        )
    }

    @Test
    fun `mergePolicySettings 在任一侧低存储时都会标记低存储`() {
        val result = mergePolicySettings(
            stored = PolicySettings(wifiConnected = true, parkingMode = true, lowStorageMode = false),
            runtime = PolicyRuntimeSignals(wifiConnected = true, parkingMode = true, lowStorageMode = true),
        )

        assertEquals(PolicySettings(wifiConnected = true, parkingMode = true, lowStorageMode = true), result)
    }
}
