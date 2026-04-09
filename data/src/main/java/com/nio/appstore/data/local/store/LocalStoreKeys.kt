package com.nio.appstore.data.local.store

object LocalStoreKeys {
    const val DOWNLOAD_ENVIRONMENT = "download_environment"
    const val DOWNLOAD_PREFERENCES = "download_preferences"
    const val POLICY_SETTINGS = "policy_settings"

    fun stagedUpgrade(appId: String): String = "staged_upgrade_$appId"
}
