package com.nio.appstore.core.installer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class RealPackageInstaller(
    context: Context,
    private val sessionAdapter: PackageInstallerSessionAdapter,
    private val sessionStore: InstallSessionStore,
    private val fallbackInstaller: PackageInstaller? = null,
) : PackageInstaller {

    private val appContext = context.applicationContext

    override suspend fun install(
        request: InstallRequest,
        onEvent: suspend (InstallEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!request.apkFile.exists()) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_MISSING, InstallFailureCode.APK_MISSING.displayText))
            return@withContext
        }
        if (request.apkFile.length() <= 0L) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_INVALID, InstallFailureCode.APK_INVALID.displayText))
            return@withContext
        }

        if (!sessionAdapter.supportsRealSession()) {
            fallbackInstaller?.install(request, onEvent)
                ?: onEvent(InstallEvent.Failed(InstallFailureCode.UNKNOWN, InstallerText.NO_AVAILABLE_INSTALLER))
            return@withContext
        }

        onEvent(InstallEvent.Waiting)
        val createdAt = System.currentTimeMillis()
        val sessionId = try {
            sessionAdapter.createSession(request)
        } catch (_: Throwable) {
            -1
        }
        if (sessionId < 0) {
            persistSession(
                InstallSessionRecord(
                    sessionId = -1,
                    appId = request.appId,
                    packageName = request.packageName,
                    apkPath = request.apkFile.absolutePath,
                    targetVersion = request.targetVersion,
                    status = InstallSessionStatus.FAILED_CREATE,
                    progress = 0,
                    failureCode = InstallFailureCode.SESSION_CREATE_FAILED.name,
                    failureMessage = InstallFailureCode.SESSION_CREATE_FAILED.displayText,
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            onEvent(InstallEvent.Failed(InstallFailureCode.SESSION_CREATE_FAILED, InstallFailureCode.SESSION_CREATE_FAILED.displayText))
            return@withContext
        }

        persistSession(
            InstallSessionRecord(
                sessionId = sessionId,
                appId = request.appId,
                packageName = request.packageName,
                apkPath = request.apkFile.absolutePath,
                targetVersion = request.targetVersion,
                status = InstallSessionStatus.CREATED,
                progress = 0,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
        onEvent(InstallEvent.SessionCreated(sessionId))
        onEvent(InstallEvent.Installing)

        delay(80L)
        val writeOk = try {
            sessionAdapter.writeApkToSession(sessionId, request.apkFile)
        } catch (_: Throwable) {
            false
        }
        if (!writeOk) {
            sessionStore.updateStatus(
                sessionId = sessionId,
                status = InstallSessionStatus.FAILED_WRITE,
                progress = 20,
                failureCode = InstallFailureCode.SESSION_WRITE_FAILED.name,
                failureMessage = InstallFailureCode.SESSION_WRITE_FAILED.displayText,
            )
            onEvent(InstallEvent.Progress(sessionId, 20))
            onEvent(InstallEvent.Failed(InstallFailureCode.SESSION_WRITE_FAILED, InstallFailureCode.SESSION_WRITE_FAILED.displayText))
            return@withContext
        }

        sessionStore.updateStatus(
            sessionId = sessionId,
            status = InstallSessionStatus.WRITTEN,
            progress = 55,
        )
        onEvent(InstallEvent.Progress(sessionId, 55))

        delay(120L)
        val commit = try {
            sessionAdapter.commitSession(sessionId)
        } catch (t: Throwable) {
            InstallCommitResult(
                success = false,
                message = t.message ?: InstallFailureCode.SESSION_COMMIT_FAILED.displayText,
                installedPackageName = null,
            )
        }

        if (!commit.success) {
            sessionStore.updateStatus(
                sessionId = sessionId,
                status = InstallSessionStatus.FAILED_COMMIT,
                progress = 85,
                failureCode = InstallFailureCode.SESSION_COMMIT_FAILED.name,
                failureMessage = commit.message,
            )
            onEvent(InstallEvent.Progress(sessionId, 85))
            onEvent(InstallEvent.Failed(InstallFailureCode.SESSION_COMMIT_FAILED, commit.message))
            return@withContext
        }

        sessionStore.updateStatus(
            sessionId = sessionId,
            status = InstallSessionStatus.CALLBACK_SUCCESS,
            progress = 100,
        )
        onEvent(InstallEvent.Progress(sessionId, 100))
        onEvent(InstallEvent.Success(request.targetVersion))
    }

    private fun persistSession(record: InstallSessionRecord) {
        sessionStore.save(record)
    }
}
