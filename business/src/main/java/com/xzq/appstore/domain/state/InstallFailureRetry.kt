package com.xzq.appstore.domain.state

/**
 * InstallFailureRetry 判断「安装失败后是否值得让用户点一次重试」。
 *
 * 安装失败分两类：
 * - 环境类（[PolicyBlocked] 覆盖的驻车 / 低存储等）：条件解除后同一份 APK 可以装成功，
 *   必须保留重试入口，否则用户驻车或清理空间后再也无法继续。
 * - 终态类：本地 APK 与目录声明不一致、APK 无法解析、平台不支持、系统不支持安装会话。
 *   这类失败再用同一份文件重试只会得到完全相同的结果，形成用户可感知的重试循环，
 *   必须改成重新下载或直接置灰。
 */
object InstallFailureRetry {
    /** 平台不支持：设备本身无法安装，重试与重新下载都无意义。 */
    const val PLATFORM_UNSUPPORTED = "PLATFORM_UNSUPPORTED"

    /** 系统不支持安装会话：当前系统能力缺失，重试无意义。 */
    const val SESSION_NOT_SUPPORTED = "SESSION_NOT_SUPPORTED"

    /** 与本地 APK 内容绑定的终态失败，重新下载一份新产物才有意义。 */
    private val ARTIFACT_TERMINAL_CODES = setOf(
        "APK_MISSING",
        "APK_INVALID",
        "APK_PACKAGE_MISMATCH",
        "APK_VERSION_MISSING",
        "APK_VERSION_MISMATCH",
        "APK_SIGNER_MISSING",
        "APK_SIGNER_MISMATCH",
    )

    /** 安装完成后与 PackageManager 事实不一致，属于环境异常，重试无意义。 */
    private val INSTALLED_FACT_MISMATCH_CODES = setOf(
        "INSTALLED_PACKAGE_MISMATCH",
        "INSTALLED_PACKAGE_NOT_FOUND",
        "INSTALLED_VERSION_MISMATCH",
        "INSTALLED_SIGNER_MISMATCH",
    )

    /** 本地产物不可用：应重新下载，而不是继续用同一份文件重试安装。 */
    fun requiresFreshArtifact(errorCode: String?): Boolean = errorCode in ARTIFACT_TERMINAL_CODES || errorCode in INSTALLED_FACT_MISMATCH_CODES

    /** 平台 / 系统能力缺失：既不重试安装，也不重新下载。 */
    fun isPlatformTerminal(errorCode: String?): Boolean =
        errorCode == PLATFORM_UNSUPPORTED || errorCode == SESSION_NOT_SUPPORTED
}
