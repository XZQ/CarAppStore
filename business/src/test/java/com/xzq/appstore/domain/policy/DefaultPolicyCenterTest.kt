package com.xzq.appstore.domain.policy

import androidx.test.core.app.ApplicationProvider
import com.xzq.appstore.core.policy.PolicyRuntimeSignalProvider
import com.xzq.appstore.core.policy.PolicyRuntimeSignals
import com.xzq.appstore.data.datasource.local.AppLocalDataSource
import com.xzq.appstore.data.model.PolicySettings
import com.xzq.appstore.domain.text.BusinessText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DefaultPolicyCenter 在构造期就要读取 Context.filesDir 和 AppLocalDataSource，
 * 且合并流依赖 StateFlow，是最能体现"Android Context + 多源数据聚合"的业务类。
 * 用 Robolectric 验证：
 * 1. 默认存储 + 健全 runtime 信号下 canDownload/canInstall/canUpgrade 给出合理判断；
 * 2. wifi / parking / lowStorage 三个开关任一翻转都让对应策略拦截；
 * 3. updateSettings 立即把新策略写回 stored（同步可观察）。
 *
 * 注意：默认 PolicyRuntimeSignals.parkingMode=false 与默认 PolicySettings.parkingMode=true
 * 合并后 parking=false，会让 canInstall 默认拦截。所以 default state 测试要显式提供
 * parkingMode=true 的 runtime 信号。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DefaultPolicyCenterTest {
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // java.io.File.usableSpace() 在路径不存在时返回 0L，会让 canDownload 误判为低存储。
        context.filesDir.mkdirs()
    }

    @Test
    fun `default state allows download install and upgrade`() {
        val center =
            DefaultPolicyCenter(
                context = context,
                localDataSource = AppLocalDataSource(context),
                runtimeSignalProvider = FakeSignalProvider(allClearSignals()),
            )

        assertTrue(center.canDownload("app-1").allow)
        assertTrue(center.canInstall("app-1").allow)
        assertTrue(center.canUpgrade("app-1").allow)
    }

    @Test
    fun `canDownload rejects with POLICY_NOT_WIFI when runtime wifi off`() {
        val center =
            DefaultPolicyCenter(
                context = context,
                localDataSource = AppLocalDataSource(context),
                runtimeSignalProvider = FakeSignalProvider(allClearSignals(wifi = false)),
            )

        val result = center.canDownload("app-1")
        assertFalse(result.allow)
        assertEquals(BusinessText.POLICY_NOT_WIFI, result.reason)
    }

    @Test
    fun `canInstall rejects with POLICY_NOT_PARKING when not in parking mode`() {
        val center =
            DefaultPolicyCenter(
                context = context,
                localDataSource = AppLocalDataSource(context),
                runtimeSignalProvider = FakeSignalProvider(allClearSignals(parking = false)),
            )

        val result = center.canInstall("app-1")
        assertFalse(result.allow)
        assertEquals(BusinessText.POLICY_NOT_PARKING, result.reason)
    }

    @Test
    fun `low storage signal blocks both download and install`() {
        val center =
            DefaultPolicyCenter(
                context = context,
                localDataSource = AppLocalDataSource(context),
                runtimeSignalProvider = FakeSignalProvider(allClearSignals(lowStorage = true)),
            )

        val downloadResult = center.canDownload("app-1")
        assertFalse(downloadResult.allow)
        assertEquals(BusinessText.POLICY_LOW_STORAGE, downloadResult.reason)

        val installResult = center.canInstall("app-1")
        assertFalse(installResult.allow)
        assertEquals(BusinessText.POLICY_LOW_STORAGE, installResult.reason)
    }

    @Test
    fun `getStoredSettings returns the manually persisted settings`() {
        val localDataSource = AppLocalDataSource(context)
        val center =
            DefaultPolicyCenter(
                context = context,
                localDataSource = localDataSource,
                runtimeSignalProvider = FakeSignalProvider(allClearSignals()),
            )

        assertTrue(center.getStoredSettings().wifiConnected)

        val updated = PolicySettings(wifiConnected = false, parkingMode = true, lowStorageMode = false)
        center.updateSettings(updated)

        // updateSettings 同步写回 storedSettingsFlow，立即可以读到新值。
        assertEquals(updated, center.getStoredSettings())
    }

    private fun allClearSignals(
        wifi: Boolean = true,
        parking: Boolean = true,
        lowStorage: Boolean = false,
    ): PolicyRuntimeSignals =
        PolicyRuntimeSignals(
            wifiConnected = wifi,
            parkingMode = parking,
            lowStorageMode = lowStorage,
        )

    private class FakeSignalProvider(
        signals: PolicyRuntimeSignals,
    ) : PolicyRuntimeSignalProvider {
        private val flow = MutableStateFlow(signals)

        override fun observeSignals(): StateFlow<PolicyRuntimeSignals> = flow

        override fun currentSignals(): PolicyRuntimeSignals = flow.value
    }
}
