package com.nio.appstore.data.datasource.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.nio.appstore.data.model.InstalledApp

class AppSystemDataSource(
    /** 应用级上下文，用于查询系统包信息和启动应用。 */
    private val context: Context,
) {
    /** 请求系统打开指定包名应用。 */
    fun openApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** 查询指定包名是否已安装。 */
    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** 查询指定包名的已安装版本号，未安装时返回 null。 */
    fun getInstalledVersion(packageName: String): String? {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** 查询指定 APK 文件的包信息，用于安装前校验。 */
    fun getPackageInfoFromApk(apkPath: String): ApkPackageInfo? {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(apkPath, 0) ?: return null
            ApkPackageInfo(
                packageName = info.packageName,
                versionName = info.versionName,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 通过 URI 查询系统已安装的全部应用列表。 */
    fun queryInstalledApps(packageNames: Set<String>): List<InstalledApp> {
        return packageNames.mapNotNull { packageName ->
            try {
                val info = context.packageManager.getPackageInfo(packageName, 0)
                val appInfo = info.applicationInfo
                val name = context.packageManager.getApplicationLabel(appInfo).toString()
                InstalledApp(
                    appId = packageName,
                    packageName = packageName,
                    name = name,
                    versionName = info.versionName,
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}

/** APK 文件解析结果。 */
data class ApkPackageInfo(
    /** APK 中声明的包名。 */
    val packageName: String,
    /** APK 中声明的版本号。 */
    val versionName: String,
)
