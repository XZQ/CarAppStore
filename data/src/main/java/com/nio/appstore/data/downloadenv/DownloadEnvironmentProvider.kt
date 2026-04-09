package com.nio.appstore.data.downloadenv

import android.content.Context
import com.nio.appstore.data.local.entity.SettingsEntity
import com.nio.appstore.data.local.store.InMemoryLocalStoreFacade
import com.nio.appstore.data.local.store.LocalStoreFacade
import com.nio.appstore.data.local.store.LocalStoreKeys

interface DownloadEnvironmentProvider {
    fun getCurrentEnvironment(): DownloadEnvironment
}

class LocalDownloadEnvironmentProvider(
    context: Context,
    private val localStoreFacade: LocalStoreFacade = InMemoryLocalStoreFacade(),
) : DownloadEnvironmentProvider {

    private val preferences = context.applicationContext.getSharedPreferences("download_env", Context.MODE_PRIVATE)

    override fun getCurrentEnvironment(): DownloadEnvironment {
        val raw = localStoreFacade.getSetting(LocalStoreKeys.DOWNLOAD_ENVIRONMENT)?.value
            ?: preferences.getString(KEY_ENV, DownloadEnvironment.DEV.name)
            ?: DownloadEnvironment.DEV.name
        return runCatching { DownloadEnvironment.valueOf(raw) }.getOrElse { DownloadEnvironment.DEV }
    }

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
