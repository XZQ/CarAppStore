package com.nio.appstore.domain.install

interface InstallManager {
    suspend fun install(appId: String)
    suspend fun clearFailed(appId: String)
}
