package com.xzq.appstore.core.policy

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AndroidPolicyRuntimeSignalProvider 是策略中心在 core 层的系统信号入口，
 * 在 init 中注册 ConnectivityManager 回调和存储广播接收器，
 * 是最依赖 Android Context 的类，用 Robolectric 验证：
 * 1. 构造不会因真实 Context 崩溃；
 * 2. 初始信号读取链路（wifiConnected / parkingMode / lowStorageMode）能给出合理默认值；
 * 3. StaticVehicleStateSignalProvider 兜底下 parkingMode 为 false，但通用平台不启用车载限制。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidPolicyRuntimeSignalProviderTest {
    @Test
    fun `init registers observers without crashing`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val provider = AndroidPolicyRuntimeSignalProvider(context = context, vehicleStateSignalProvider = StaticVehicleStateSignalProvider())

        assertNotNull(provider.currentSignals())
    }

    @Test
    fun `static vehicle provider defaults to non parking mode`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val provider = AndroidPolicyRuntimeSignalProvider(context = context, vehicleStateSignalProvider = StaticVehicleStateSignalProvider())

        assertFalse(provider.currentSignals().parkingMode)
        assertFalse(provider.currentSignals().vehicleInstallPolicyEnabled)
    }

    @Test
    fun `vehicle install policy is enabled only when explicitly configured`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val provider = AndroidPolicyRuntimeSignalProvider(
            context = context,
            vehicleStateSignalProvider = StaticVehicleStateSignalProvider(),
            vehicleInstallPolicyEnabled = true,
        )

        assertTrue(provider.currentSignals().vehicleInstallPolicyEnabled)
    }

    @Test
    fun `low storage mode is false when temp filesDir has ample space`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val provider = AndroidPolicyRuntimeSignalProvider(context = context, vehicleStateSignalProvider = StaticVehicleStateSignalProvider())

        // Robolectric 默认 filesDir 指向临时目录，可用空间充足，应判非低存储。
        assertFalse(provider.currentSignals().lowStorageMode)
    }

    @Test
    fun `observeSignals exposes a StateFlow with a snapshot`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val provider = AndroidPolicyRuntimeSignalProvider(context = context, vehicleStateSignalProvider = StaticVehicleStateSignalProvider())

        val flow = provider.observeSignals()
        assertNotNull(flow.value)
    }
}
