package com.xzq.appstore.data.local.store

object LocalStoreKeys {
    /** 下载环境设置项键名。 */
    const val DOWNLOAD_ENVIRONMENT = "download_environment"
    /** 下载偏好设置项键名。 */
    const val DOWNLOAD_PREFERENCES = "download_preferences"
    /** 策略设置项键名。 */
    const val POLICY_SETTINGS = "policy_settings"
    const val RECENTLY_OPENED_PACKAGES = "recently_opened_packages"

    /** 生成指定应用的暂存升级版本键名。 */
    fun stagedUpgrade(appId: String): String = "staged_upgrade_$appId"
}
