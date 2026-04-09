package com.nio.appstore.domain.policy

import android.content.Context
import com.nio.appstore.data.datasource.local.AppLocalDataSource
import com.nio.appstore.data.model.PolicySettings
import com.nio.appstore.domain.text.BusinessText

class DefaultPolicyCenter(
    private val context: Context,
    private val localDataSource: AppLocalDataSource,
) : PolicyCenter {

    override fun canDownload(appId: String): PolicyResult {
        val settings = getSettings()
        return when {
            !settings.wifiConnected -> PolicyResult(false, BusinessText.POLICY_NOT_WIFI)
            settings.lowStorageMode -> PolicyResult(false, BusinessText.POLICY_LOW_STORAGE)
            context.filesDir.usableSpace < MIN_REQUIRED_SPACE_BYTES -> PolicyResult(false, BusinessText.POLICY_DEVICE_STORAGE_LOW)
            else -> PolicyResult(true)
        }
    }

    override fun canInstall(appId: String): PolicyResult {
        val settings = getSettings()
        return when {
            !settings.parkingMode -> PolicyResult(false, BusinessText.POLICY_NOT_PARKING)
            settings.lowStorageMode -> PolicyResult(false, BusinessText.POLICY_LOW_STORAGE)
            else -> PolicyResult(true)
        }
    }

    override fun canUpgrade(appId: String): PolicyResult {
        val downloadPolicy = canDownload(appId)
        if (!downloadPolicy.allow) return downloadPolicy
        val installPolicy = canInstall(appId)
        if (!installPolicy.allow) return installPolicy
        return PolicyResult(true)
    }

    override fun getSettings(): PolicySettings = localDataSource.getPolicySettings()

    override fun updateSettings(settings: PolicySettings) {
        localDataSource.savePolicySettings(settings)
    }

    companion object {
        private const val MIN_REQUIRED_SPACE_BYTES = 8L * 1024L * 1024L
    }
}
