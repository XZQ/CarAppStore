package com.nio.appstore.domain.upgrade

interface UpgradeManager {
    suspend fun startUpgrade(appId: String)
    suspend fun checkUpgrade(appId: String): Boolean
}
