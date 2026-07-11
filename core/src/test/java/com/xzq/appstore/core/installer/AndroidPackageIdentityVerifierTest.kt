package com.xzq.appstore.core.installer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class AndroidPackageIdentityVerifierTest {
    private lateinit var context: Context
    private lateinit var verifier: AndroidPackageIdentityVerifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        verifier = AndroidPackageIdentityVerifier(context)
    }

    @Test
    fun `verify 不可解析 APK 时返回 APK_INVALID`() {
        val result = verifier.verify(
            File(context.cacheDir, "missing.apk"),
            ExpectedApkIdentity(TEST_PACKAGE_NAME, TEST_VERSION_CODE, TEST_VERSION_NAME, setOf(TEST_SIGNER)),
            ApkVerificationPolicy(requireVersionCode = true, requireSignerCertificate = true),
        )

        assertTrue(result is ApkVerificationResult.Rejected)
        assertEquals(InstallFailureCode.APK_INVALID, (result as ApkVerificationResult.Rejected).code)
    }

    @Test
    fun `getInstalledIdentity 读取系统包版本和签名摘要`() {
        installFakePackage()

        val identity = requireNotNull(verifier.getInstalledIdentity(TEST_PACKAGE_NAME))

        assertEquals(TEST_PACKAGE_NAME, identity.packageName)
        assertEquals(TEST_VERSION_CODE, identity.versionCode)
        assertEquals(TEST_VERSION_NAME, identity.versionName)
        assertEquals(setOf(sha256(TEST_SIGNATURE_BYTES)), identity.signerCertificateSha256)
    }

    @Suppress("DEPRECATION")
    private fun installFakePackage() {
        val packageInfo = PackageInfo().apply {
            packageName = TEST_PACKAGE_NAME
            versionCode = TEST_VERSION_CODE.toInt()
            versionName = TEST_VERSION_NAME
            signatures = arrayOf(Signature(TEST_SIGNATURE_BYTES))
            applicationInfo = ApplicationInfo().apply { packageName = TEST_PACKAGE_NAME }
        }
        shadowOf(context.packageManager).installPackage(packageInfo)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TEST_PACKAGE_NAME = "com.example.identity"
        const val TEST_VERSION_CODE = 42L
        const val TEST_VERSION_NAME = "4.2.0"
        const val TEST_SIGNER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val TEST_SIGNATURE_BYTES = byteArrayOf(1, 2, 3, 4, 5)
    }
}
