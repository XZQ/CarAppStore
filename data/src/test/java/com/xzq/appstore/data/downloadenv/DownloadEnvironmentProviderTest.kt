package com.xzq.appstore.data.downloadenv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xzq.appstore.data.local.entity.SettingsEntity
import com.xzq.appstore.data.local.store.InMemoryLocalStoreFacade
import com.xzq.appstore.data.local.store.LocalStoreKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DownloadEnvironmentProviderTest 验证 Debug 环境切换和 Release 生产环境锁定边界。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEnvironmentProviderTest {
    private lateinit var context: Context

    /** 清理旧版 SharedPreferences，避免不同用例共享环境选择。 */
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("download_env", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Release 模式忽略历史 LOCAL_SIM 值并始终返回 PROD。 */
    @Test
    fun `release mode ignores persisted environment and returns production`() {
        val store = InMemoryLocalStoreFacade().apply {
            saveSetting(SettingsEntity(LocalStoreKeys.DOWNLOAD_ENVIRONMENT, DownloadEnvironment.LOCAL_SIM.name, 1L))
        }
        val provider = LocalDownloadEnvironmentProvider(
            context = context,
            localStoreFacade = store,
            defaultEnvironment = DownloadEnvironment.PROD,
            environmentSwitchingAllowed = false,
        )

        assertEquals(DownloadEnvironment.PROD, provider.getCurrentEnvironment())
    }

    /** Release 模式拒绝通过代码切换到非生产环境。 */
    @Test
    fun `release mode rejects environment switching`() {
        val provider = LocalDownloadEnvironmentProvider(
            context = context,
            defaultEnvironment = DownloadEnvironment.PROD,
            environmentSwitchingAllowed = false,
        )

        assertThrows(IllegalStateException::class.java) { provider.setCurrentEnvironment(DownloadEnvironment.LOCAL_SIM) }
    }

    /** Debug 模式继续持久化并读取 LOCAL_SIM 环境。 */
    @Test
    fun `debug mode persists selected environment`() {
        val provider = LocalDownloadEnvironmentProvider(
            context = context,
            defaultEnvironment = DownloadEnvironment.DEV,
            environmentSwitchingAllowed = true,
        )

        provider.setCurrentEnvironment(DownloadEnvironment.LOCAL_SIM)

        assertEquals(DownloadEnvironment.LOCAL_SIM, provider.getCurrentEnvironment())
    }
}
