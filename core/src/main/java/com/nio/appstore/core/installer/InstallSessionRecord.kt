package com.nio.appstore.core.installer

data class InstallSessionRecord(
    val sessionId: Int = -1,
    val appId: String,
    val packageName: String,
    val apkPath: String,
    val targetVersion: String,
    val status: String,
    val progress: Int = 0,
    val failureCode: String? = null,
    val failureMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
