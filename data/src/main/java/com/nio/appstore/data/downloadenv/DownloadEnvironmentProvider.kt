package com.nio.appstore.data.downloadenv

import android.content.Context
import com.nio.appstore.data.local.entity.SettingsEntity
import com.nio.appstore.data.local.store.InMemoryLocalStoreFacade
import com.nio.appstore.data.local.store.LocalStoreFacade
import com.nio.appstore.data.local.store.LocalStoreKeys

interface DownloadEnvironmentProvider {
    /** 读取当前生效的下载环境。 */
    fun getCurrentEnvironment(): DownloadEnvironment
}

class LocalDownloadEnvironmentProvider(
    context: Context,
    /** 结构化本地存储入口，优先用于持久化下载环境。 */
    private val localStoreFacade: LocalStoreFacade = InMemoryLocalStoreFacade(),
) : DownloadEnvironmentProvider {

    /** 旧版环境配置仍使用 SharedPreferences 兜底兼容。 */
    private val preferences = context.applicationContext.getSharedPreferences("download_env", Context.MODE_PRIVATE)

    /** 按“结构化存储优先，SP 兜底”的顺序读取当前环境。 */
    override fun getCurrentEnvironment(): DownloadEnvironment {
        val raw = localStoreFacade.getSetting(LocalStoreKeys.DOWNLOAD_ENVIRONMENT)?.value
            ?: preferences.getString(KEY_ENV, DownloadEnvironment.DEV.name)
            ?: DownloadEnvironment.DEV.name
        return runCatching { DownloadEnvironment.valueOf(raw) }.getOrElse { DownloadEnvironment.DEV }
    }

    /** 同时写入结构化存储和旧版 SP，保证新老路径都能读到同一环境。 */
    fun setCurrentEnvironment(environment: DownloadEnvironment) {
        localStoreFacade.saveSetting(
            SettingsEntity(
                key = LocalStoreKeys.DOWNLOAD_ENVIRONMENT,
                value = environment.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
        preferences.edit().putString(KEY_ENV, environment.name).apply()
    }

    companion object {
        private const val KEY_ENV = "current_download_environment"
    }
}
