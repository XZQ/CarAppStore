package com.xzq.appstore.core.policy

import org.junit.Assert.assertFalse
import org.junit.Test

class PolicyRuntimeSignalsTest {

    @Test
    fun `VehicleRuntimeState 默认值应按安全策略视为非驻车`() {
        assertFalse(VehicleRuntimeState().parkingMode)
    }

    @Test
    fun `PolicyRuntimeSignals 默认值应按安全策略视为非驻车`() {
        assertFalse(PolicyRuntimeSignals().parkingMode)
    }

    @Test
    fun `PolicyRuntimeSignals 默认不启用车载安装限制`() {
        assertFalse(PolicyRuntimeSignals().vehicleInstallPolicyEnabled)
    }
}
