package com.xzq.appstore.core.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkIdentityValidatorTest {

    @Test
    fun `validate 包名不一致时拒绝`() {
        val result = ApkIdentityValidator.validate(actual(packageName = "com.attacker.app"), expected(), STRICT_POLICY)

        assertRejected(InstallFailureCode.APK_PACKAGE_MISMATCH, result)
    }

    @Test
    fun `validate 严格策略缺少版本代码时拒绝`() {
        val result = ApkIdentityValidator.validate(actual(), expected(versionCode = 0L), STRICT_POLICY)

        assertRejected(InstallFailureCode.APK_VERSION_MISSING, result)
    }

    @Test
    fun `validate 版本代码不一致时拒绝`() {
        val result = ApkIdentityValidator.validate(actual(versionCode = 101L), expected(versionCode = 100L), STRICT_POLICY)

        assertRejected(InstallFailureCode.APK_VERSION_MISMATCH, result)
    }

    @Test
    fun `validate 本地策略缺少版本代码时比较版本名`() {
        val result = ApkIdentityValidator.validate(actual(versionName = "1.2.3"), expected(versionCode = 0L, versionName = "1.2.3"), LOCAL_POLICY)

        assertTrue(result is ApkVerificationResult.Verified)
    }

    @Test
    fun `validate 本地策略版本名不一致时拒绝`() {
        val result = ApkIdentityValidator.validate(actual(versionName = "1.2.4"), expected(versionCode = 0L, versionName = "1.2.3"), LOCAL_POLICY)

        assertRejected(InstallFailureCode.APK_VERSION_MISMATCH, result)
    }

    @Test
    fun `validate 严格策略缺少签名摘要时拒绝`() {
        val result = ApkIdentityValidator.validate(actual(), expected(signers = emptySet()), STRICT_POLICY)

        assertRejected(InstallFailureCode.APK_SIGNER_MISSING, result)
    }

    @Test
    fun `validate 签名摘要不匹配时拒绝`() {
        val result = ApkIdentityValidator.validate(actual(signers = setOf(OTHER_SIGNER)), expected(), STRICT_POLICY)

        assertRejected(InstallFailureCode.APK_SIGNER_MISMATCH, result)
    }

    @Test
    fun `validate 签名白名单与实际签名有交集时通过`() {
        val result = ApkIdentityValidator.validate(
            actual(signers = setOf(OTHER_SIGNER, TRUSTED_SIGNER.uppercase())),
            expected(signers = setOf(TRUSTED_SIGNER)),
            STRICT_POLICY,
        )

        assertTrue(result is ApkVerificationResult.Verified)
    }

    @Test
    fun `validate 本地策略允许缺少签名摘要`() {
        val result = ApkIdentityValidator.validate(actual(), expected(versionCode = 0L, signers = emptySet()), LOCAL_POLICY)

        assertTrue(result is ApkVerificationResult.Verified)
    }

    private fun actual(
        packageName: String = TEST_PACKAGE_NAME,
        versionCode: Long = TEST_VERSION_CODE,
        versionName: String = TEST_VERSION_NAME,
        signers: Set<String> = setOf(TRUSTED_SIGNER),
    ): ApkIdentity = ApkIdentity(packageName, versionCode, versionName, signers)

    private fun expected(
        packageName: String = TEST_PACKAGE_NAME,
        versionCode: Long = TEST_VERSION_CODE,
        versionName: String = TEST_VERSION_NAME,
        signers: Set<String> = setOf(TRUSTED_SIGNER),
    ): ExpectedApkIdentity = ExpectedApkIdentity(packageName, versionCode, versionName, signers)

    private fun assertRejected(expectedCode: InstallFailureCode, result: ApkVerificationResult) {
        assertTrue(result is ApkVerificationResult.Rejected)
        assertEquals(expectedCode, (result as ApkVerificationResult.Rejected).code)
    }

    private companion object {
        const val TEST_PACKAGE_NAME = "com.example.app"
        const val TEST_VERSION_CODE = 100L
        const val TEST_VERSION_NAME = "1.2.3"
        const val TRUSTED_SIGNER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OTHER_SIGNER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val STRICT_POLICY = ApkVerificationPolicy(requireVersionCode = true, requireSignerCertificate = true)
        val LOCAL_POLICY = ApkVerificationPolicy(requireVersionCode = false, requireSignerCertificate = false)
    }
}
