package com.nio.appstore.data.local.entity

data class InstallSessionEntity(
    val sessionId: Int,
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
