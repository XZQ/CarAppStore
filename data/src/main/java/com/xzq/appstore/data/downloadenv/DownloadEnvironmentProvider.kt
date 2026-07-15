package com.xzq.appstore.data.downloadenv

import android.content.Context
import com.xzq.appstore.data.BuildConfig
import com.xzq.appstore.data.local.entity.SettingsEntity
import com.xzq.appstore.data.local.store.InMemoryLocalStoreFacade
import com.xzq.appstore.data.local.store.LocalStoreFacade
import com.xzq.appstore.data.local.store.LocalStoreKeys

interface DownloadEnvironmentProvider {
    /** 读取当前生效的下载环境。 */
    fun getCurrentEnvironment(): DownloadEnvironment
}

/**
 * LocalDownloadEnvironmentProvider 管理当前下载环境，并把 Release 固定在生产环境。
 *
 * Debug 构建允许通过开发者设置切换 DEV、TEST、PROD 与 LOCAL_SIM；Release 构建忽略
 * 历史调试配置并始终返回 PROD，避免本地模拟地址或 Mock 下载能力进入生产运行链路。
 */
class LocalDownloadEnvironmentProvider(
    context: Context,
    /** 结构化本地存储入口，优先用于持久化下载环境。 */
    private val localStoreFacade: LocalStoreFacade = InMemoryLocalStoreFacade(),
    /** 当前构建未保存环境选择时使用的安全默认环境。 */
    private val defaultEnvironment: DownloadEnvironment = if (BuildConfig.DEBUG) DownloadEnvironment.DEV else DownloadEnvironment.PROD,
    /** 是否允许读取和修改持久化的环境选择，仅 Debug 构建默认开启。 */
    private val environmentSwitchingAllowed: Boolean = BuildConfig.DEBUG,
) : DownloadEnvironmentProvider {

    /** 旧版环境配置仍使用 SharedPreferences 兜底兼容。 */
    private val preferences = context.applicationContext.getSharedPreferences("download_env", Context.MODE_PRIVATE)

    /** 按“结构化存储优先，SP 兜底”的顺序读取当前环境。 */
    override fun getCurrentEnvironment(): DownloadEnvironment {
        if (!environmentSwitchingAllowed) {
            return defaultEnvironment
        }
        val raw = localStoreFacade.getSetting(LocalStoreKeys.DOWNLOAD_ENVIRONMENT)?.value ?: preferences.getString(KEY_ENV, defaultEnvironment.name) ?: defaultEnvironment.name
        return runCatching { DownloadEnvironment.valueOf(raw) }.getOrElse { defaultEnvironment }
    }

    /** 同时写入结构化存储和旧版 SP，保证新老路径都能读到同一环境。 */
    fun setCurrentEnvironment(environment: DownloadEnvironment) {
        check(environmentSwitchingAllowed) { "Release 构建不允许切换下载环境" }
        localStoreFacade.saveSetting(
            SettingsEntity(key = LocalStoreKeys.DOWNLOAD_ENVIRONMENT, value = environment.name, updatedAt = System.currentTimeMillis())
        )
        preferences.edit().putString(KEY_ENV, environment.name).apply()
    }

    companion object {
        private const val KEY_ENV = "current_download_environment"
    }
}
