package com.nio.appstore.data.local.mapper

import com.nio.appstore.core.installer.InstallSessionRecord
import com.nio.appstore.data.local.entity.InstallSessionEntity

object InstallSessionMapper {
    fun toEntity(record: InstallSessionRecord): InstallSessionEntity = InstallSessionEntity(
        sessionId = record.sessionId,
        appId = record.appId,
        packageName = record.packageName,
        apkPath = record.apkPath,
        targetVersion = record.targetVersion,
        status = record.status,
        progress = record.progress,
        failureCode = record.failureCode,
        failureMessage = record.failureMessage,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
    )

    fun fromEntity(entity: InstallSessionEntity): InstallSessionRecord = InstallSessionRecord(
        sessionId = entity.sessionId,
        appId = entity.appId,
        packageName = entity.packageName,
        apkPath = entity.apkPath,
        targetVersion = entity.targetVersion,
        status = entity.status,
        progress = entity.progress,
        failureCode = entity.failureCode,
        failureMessage = entity.failureMessage,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )
}
