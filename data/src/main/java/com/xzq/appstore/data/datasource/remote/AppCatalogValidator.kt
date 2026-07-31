package com.xzq.appstore.data.datasource.remote

import com.xzq.appstore.data.model.AppPlatform

/** 校验远端目录中会进入安装与文件系统边界的关键字段。 */
object AppCatalogValidator {
    /** 校验整个目录并原样返回合法条目。 */
    fun validate(items: List<AppCatalogItemResponse>): List<AppCatalogItemResponse> {
        items.forEach(::validateItem)
        require(items.map { it.appId }.distinct().size == items.size) { "目录包含重复 appId" }
        val packageNames = items.map { it.packageName }.filter { it.isNotBlank() }
        require(packageNames.distinct().size == packageNames.size) { "目录包含重复 packageName" }
        return items
    }

    /** 校验单个目录项的稳定标识与 APK 身份元数据。 */
    private fun validateItem(item: AppCatalogItemResponse) {
        require(APP_ID_PATTERN.matches(item.appId)) { "非法目录 appId: ${item.appId}" }
        if (AppPlatform.ANDROID in item.supportedPlatforms) {
            require(PACKAGE_NAME_PATTERN.matches(item.packageName)) { "Android 目录项缺少合法 packageName: ${item.appId}" }
        } else if (item.packageName.isNotBlank()) {
            require(PACKAGE_NAME_PATTERN.matches(item.packageName)) { "非法目录 packageName: ${item.packageName}" }
        }
        require(item.versionName.isNotBlank() && item.versionName.length <= MAX_VERSION_NAME_LENGTH) { "非法目录 versionName: ${item.appId}" }
        require(item.versionCode >= 0L) { "非法目录 versionCode: ${item.appId}" }
        require(item.signerCertificateSha256.all(SIGNER_SHA256_PATTERN::matches)) { "非法目录签名摘要: ${item.appId}" }
    }

    private val APP_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val SIGNER_SHA256_PATTERN = Regex("[A-Fa-f0-9]{64}")
    private const val MAX_VERSION_NAME_LENGTH = 128
}
