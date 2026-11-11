package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.data.model.AppPlatform

/**
 * 目录信任元数据的强制度。
 *
 * 安装链路依靠目录声明的 versionCode 与签名摘要做「目录 → APK → 已安装包」一致性校验，
 * 缺失时安装会被安全策略拒绝。提前在目录解析阶段拒绝，可以让整份目录在进入 UI 前失败关闭，
 * 而不是让用户点到安装按钮才看到错误。Debug + LOCAL_SIM 的本地联调目录例外：
 * 手工构造的 APK 未必带签名摘要，此时用 [RELAXED] 放行，但版本号仍不得为负。
 */
data class AppCatalogTrustPolicy(
    /** 是否要求 versionCode 为正数（Android 不接受 versionCode <= 0 的包）。 */
    val requirePositiveVersionCode: Boolean = true,
    /** 是否要求至少提供一个签名证书摘要。 */
    val requireSignerCertificate: Boolean = true,
) {
    companion object {
        /** 生产 / 测试 / 开发环境的默认策略：信任元数据必须齐全。 */
        val STRICT = AppCatalogTrustPolicy()

        /** 仅用于 Debug + LOCAL_SIM：允许缺失签名摘要与 versionCode。 */
        val RELAXED = AppCatalogTrustPolicy(
            requirePositiveVersionCode = false,
            requireSignerCertificate = false,
        )
    }
}

/** 校验远端目录中会进入安装与文件系统边界的关键字段。 */
object AppCatalogValidator {
    /** 校验整个目录并原样返回合法条目。 */
    fun validate(
        items: List<AppCatalogItemResponse>,
        trustPolicy: AppCatalogTrustPolicy = AppCatalogTrustPolicy.STRICT,
    ): List<AppCatalogItemResponse> {
        items.forEach { validateItem(it, trustPolicy) }
        require(items.map { it.appId }.distinct().size == items.size) { "目录包含重复 appId" }
        val packageNames = items.map { it.packageName }.filter { it.isNotBlank() }
        require(packageNames.distinct().size == packageNames.size) { "目录包含重复 packageName" }
        return items
    }

    /** 校验单个目录项的稳定标识与 APK 身份元数据。 */
    private fun validateItem(item: AppCatalogItemResponse, trustPolicy: AppCatalogTrustPolicy) {
        require(APP_ID_PATTERN.matches(item.appId)) { "非法目录 appId: ${item.appId}" }
        if (AppPlatform.ANDROID in item.supportedPlatforms) {
            require(PACKAGE_NAME_PATTERN.matches(item.packageName)) { "Android 目录项缺少合法 packageName: ${item.appId}" }
        } else if (item.packageName.isNotBlank()) {
            require(PACKAGE_NAME_PATTERN.matches(item.packageName)) { "非法目录 packageName: ${item.packageName}" }
        }
        require(item.versionName.isNotBlank() && item.versionName.length <= MAX_VERSION_NAME_LENGTH) { "非法目录 versionName: ${item.appId}" }
        require(item.versionCode >= 0L) { "非法目录 versionCode: ${item.appId}" }
        require(item.signerCertificateSha256.all(SIGNER_SHA256_PATTERN::matches)) { "非法目录签名摘要: ${item.appId}" }
        // 版本号与签名摘要只对可安装到 Android 的条目有意义，纯展示的其他平台条目不强制。
        if (AppPlatform.ANDROID in item.supportedPlatforms) {
            if (trustPolicy.requirePositiveVersionCode) {
                require(item.versionCode > 0L) { "目录缺少有效 versionCode: ${item.appId}" }
            }
            if (trustPolicy.requireSignerCertificate) {
                require(item.signerCertificateSha256.isNotEmpty()) { "目录缺少签名摘要: ${item.appId}" }
            }
            validateChecksum(item)
        }
    }

    /**
     * 校验下载链路实际支持的校验算法与摘要格式。
     *
     * 未知算法（如 SHA-512）或错配摘要若放行，会在下载完成时才以 CHECKSUM_MISMATCH 失败，
     * 且产物级失败重试会清理分片重新拉取，把目录配置错误放大成反复下载。提前在目录阶段失败关闭。
     * 允许只声明算法不带摘要值（下载源目录的既有形态）；只有摘要值没有算法则无法校验，拒绝。
     */
    private fun validateChecksum(item: AppCatalogItemResponse) {
        val type = item.checksumType?.trim()?.uppercase()
        val value = item.checksumValue?.trim()
        if (type == null) {
            require(value == null) { "目录 checksumValue 缺少 checksumType: ${item.appId}" }
            return
        }
        val digestPattern = when (type) {
            "SHA-256", "SHA256" -> SHA256_DIGEST_PATTERN
            "MD5" -> MD5_DIGEST_PATTERN
            else -> null
        }
        requireNotNull(digestPattern) { "目录声明了不支持的 checksumType: ${item.appId}" }
        if (value != null) {
            require(digestPattern.matches(value)) { "目录 checksumValue 与 checksumType 不匹配: ${item.appId}" }
        }
    }

    private val APP_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val SIGNER_SHA256_PATTERN = Regex("[A-Fa-f0-9]{64}")
    private val SHA256_DIGEST_PATTERN = Regex("[A-Fa-f0-9]{64}")
    private val MD5_DIGEST_PATTERN = Regex("[A-Fa-f0-9]{32}")
    private const val MAX_VERSION_NAME_LENGTH = 128
}
