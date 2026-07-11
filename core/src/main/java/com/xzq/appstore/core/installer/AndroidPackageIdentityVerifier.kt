package com.xzq.appstore.core.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** 使用 PackageManager 读取 APK archive 与已安装包的实际身份。 */
class AndroidPackageIdentityVerifier(context: Context) : ApkVerifier, InstalledPackageInspector {
    private val packageManager = context.applicationContext.packageManager

    /** 读取 APK archive 并执行纯身份规则校验。 */
    override fun verify(apkFile: File, expected: ExpectedApkIdentity, policy: ApkVerificationPolicy): ApkVerificationResult {
        val actual = readArchiveIdentity(apkFile) ?: return ApkVerificationResult.Rejected(InstallFailureCode.APK_INVALID)
        return ApkIdentityValidator.validate(actual, expected, policy)
    }

    /** 查询 PackageManager 中已经安装的最终包身份。 */
    override fun getInstalledIdentity(packageName: String): ApkIdentity? {
        return runCatching { packageManager.getPackageInfo(packageName, signingFlags()).toIdentity() }.getOrNull()
    }

    /** 从指定 APK 文件读取 manifest 与签名信息。 */
    private fun readArchiveIdentity(apkFile: File): ApkIdentity? {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return null
        }
        return runCatching { packageManager.getPackageArchiveInfo(apkFile.absolutePath, signingFlags())?.toIdentity() }.getOrNull()
    }

    /** 把 PackageInfo 统一转换成跨版本身份模型。 */
    private fun PackageInfo.toIdentity(): ApkIdentity {
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else legacyVersionCode()
        return ApkIdentity(
            packageName = packageName,
            versionCode = code,
            versionName = versionName.orEmpty(),
            signerCertificateSha256 = readSignatures().map(::sha256).toSet(),
        )
    }

    /** 根据系统版本选择 PackageManager 的签名读取标记。 */
    @Suppress("DEPRECATION")
    private fun signingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    }

    /** API 26–27 使用旧版 versionCode 字段。 */
    @Suppress("DEPRECATION")
    private fun PackageInfo.legacyVersionCode(): Long = versionCode.toLong()

    /** 兼容单签名轮换、多签名包和 API 26–27 的签名集合读取。 */
    @Suppress("DEPRECATION")
    private fun PackageInfo.readSignatures(): Array<Signature> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return signatures ?: emptyArray()
        }
        val info = signingInfo ?: return emptyArray()
        return if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
    }

    /** 计算证书 DER 字节的 SHA-256 摘要。 */
    private fun sha256(signature: Signature): String {
        return MessageDigest.getInstance(SHA_256).digest(signature.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val SHA_256 = "SHA-256"
    }
}
