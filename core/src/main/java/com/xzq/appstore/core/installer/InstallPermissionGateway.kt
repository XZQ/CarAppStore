package com.xzq.appstore.core.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 安装未知来源 APK 所需的系统权限边界。 */
interface InstallPermissionGateway {
    /** 当前应用是否已获系统授予的安装未知应用权限。 */
    fun canRequestInstalls(): Boolean
}

/** Android 平台上的安装权限实现。 */
class AndroidInstallPermissionGateway(context: Context) : InstallPermissionGateway {
    /** 应用级上下文，避免持有 Activity。 */
    private val appContext = context.applicationContext

    /** 查询系统的未知来源安装授权状态。 */
    override fun canRequestInstalls(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    /** 创建当前应用的未知来源安装授权设置页 Intent。 */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
}
