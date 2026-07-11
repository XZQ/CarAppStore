package com.xzq.appstore.core.installer

import java.io.File

/** PackageManager 从 APK 或已安装包读取出的实际身份。 */
data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signerCertificateSha256: Set<String>,
)

/** 目录声明的预期 APK 身份。 */
data class ExpectedApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signerCertificateSha256: Set<String>,
)

/** 控制缺失版本代码或签名摘要时是否失败关闭。 */
data class ApkVerificationPolicy(
    val requireVersionCode: Boolean,
    val requireSignerCertificate: Boolean,
)

/** APK 身份校验的结构化结果。 */
sealed class ApkVerificationResult {
    data class Verified(val identity: ApkIdentity) : ApkVerificationResult()
    data class Rejected(val code: InstallFailureCode, val message: String = code.displayText) : ApkVerificationResult()
}

/** 安装前读取并校验 APK archive。 */
interface ApkVerifier {
    fun verify(apkFile: File, expected: ExpectedApkIdentity, policy: ApkVerificationPolicy): ApkVerificationResult
}

/** 安装成功回调后读取 PackageManager 中的最终包身份。 */
interface InstalledPackageInspector {
    fun getInstalledIdentity(packageName: String): ApkIdentity?
}

/** 不依赖 Android 框架的 APK 身份规则。 */
object ApkIdentityValidator {
    fun validate(actual: ApkIdentity, expected: ExpectedApkIdentity, policy: ApkVerificationPolicy): ApkVerificationResult {
        if (actual.packageName != expected.packageName) {
            return ApkVerificationResult.Rejected(InstallFailureCode.APK_PACKAGE_MISMATCH)
        }
        val versionFailure = validateVersion(actual, expected, policy)
        if (versionFailure != null) {
            return versionFailure
        }
        val signerFailure = validateSigners(actual, expected, policy)
        if (signerFailure != null) {
            return signerFailure
        }
        return ApkVerificationResult.Verified(actual.copy(signerCertificateSha256 = normalizeSigners(actual.signerCertificateSha256)))
    }

    private fun validateVersion(
        actual: ApkIdentity,
        expected: ExpectedApkIdentity,
        policy: ApkVerificationPolicy,
    ): ApkVerificationResult.Rejected? {
        if (expected.versionCode > 0L) {
            return if (actual.versionCode == expected.versionCode) null else ApkVerificationResult.Rejected(InstallFailureCode.APK_VERSION_MISMATCH)
        }
        if (policy.requireVersionCode) {
            return ApkVerificationResult.Rejected(InstallFailureCode.APK_VERSION_MISSING)
        }
        return if (actual.versionName == expected.versionName) null else ApkVerificationResult.Rejected(InstallFailureCode.APK_VERSION_MISMATCH)
    }

    private fun validateSigners(
        actual: ApkIdentity,
        expected: ExpectedApkIdentity,
        policy: ApkVerificationPolicy,
    ): ApkVerificationResult.Rejected? {
        val expectedSigners = normalizeSigners(expected.signerCertificateSha256)
        if (expectedSigners.isEmpty()) {
            return if (policy.requireSignerCertificate) ApkVerificationResult.Rejected(InstallFailureCode.APK_SIGNER_MISSING) else null
        }
        val actualSigners = normalizeSigners(actual.signerCertificateSha256)
        return if (actualSigners.any(expectedSigners::contains)) null else ApkVerificationResult.Rejected(InstallFailureCode.APK_SIGNER_MISMATCH)
    }

    private fun normalizeSigners(signers: Set<String>): Set<String> = signers.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
}
