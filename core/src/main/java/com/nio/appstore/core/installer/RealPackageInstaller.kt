package com.nio.appstore.core.installer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class RealPackageInstaller(
    /** 系统安装会话适配层，负责与平台 Session API 交互。 */
    private val sessionAdapter: PackageInstallerSessionAdapter,
    /** 安装会话持久化存储。 */
    private val sessionStore: InstallSessionStore,
    /** 平台不支持真实安装会话时使用的兜底安装器。 */
    private val fallbackInstaller: PackageInstaller? = null,
) : PackageInstaller {

    /**
     * 执行真实安装流程。
     *
     * 这个方法负责 APK 基础校验、系统会话创建、APK 写入、提交以及最终结果回收。
     */
    override suspend fun install(
        request: InstallRequest,
        onEvent: suspend (InstallEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // 安装前先做最基本的文件有效性校验，避免无效 APK 进入系统会话。
        if (!request.apkFile.exists()) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_MISSING, InstallFailureCode.APK_MISSING.displayText))
            return@withContext
        }
        if (request.apkFile.length() <= 0L) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_INVALID, InstallFailureCode.APK_INVALID.displayText))
            return@withContext
        }

        // 平台不支持真实会话时，统一回退到兜底安装器。
        if (!sessionAdapter.supportsRealSession()) {
            fallbackInstaller?.install(request, onEvent)
                ?: onEvent(InstallEvent.Failed(InstallFailureCode.UNKNOWN, InstallerText.NO_AVAILABLE_INSTALLER))
            return@withContext
        }

        onEvent(InstallEvent.Waiting)
        val createdAt = System.currentTimeMillis()
        // 第一步创建系统安装会话，并在失败时立即记录失败会话。
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

        // 会话创建成功后先持久化 CREATED 状态，保证安装中心能看到新会话。
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

        // 第二步把 APK 内容写入系统会话。
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

        // APK 写入成功后，更新会话进度，供安装中心展示中间阶段。
        sessionStore.updateStatus(
            sessionId = sessionId,
            status = InstallSessionStatus.WRITTEN,
            progress = 55,
        )
        onEvent(InstallEvent.Progress(sessionId, 55))

        // 第三步提交系统会话，并在需要用户确认时把中间态抛给上层。
        delay(120L)
        sessionStore.updateStatus(
            sessionId = sessionId,
            status = InstallSessionStatus.COMMITTED,
            progress = 80,
        )
        onEvent(InstallEvent.Progress(sessionId, 80))
        val commit = try {
            sessionAdapter.commitSession(sessionId) { message, confirmationIntent ->
                // 进入系统确认阶段后，先把会话状态持久化，再通知壳层拉起确认页。
                sessionStore.updateStatus(
                    sessionId = sessionId,
                    status = InstallSessionStatus.PENDING_USER_ACTION,
                    progress = 90,
                )
                onEvent(InstallEvent.Progress(sessionId, 90))
                onEvent(
                    InstallEvent.PendingUserAction(
                        sessionId = sessionId,
                        message = message,
                        confirmationIntent = confirmationIntent,
                    )
                )
            }
        } catch (t: Throwable) {
            InstallCommitResult(
                success = false,
                message = t.message ?: InstallFailureCode.SESSION_COMMIT_FAILED.displayText,
                installedPackageName = null,
            )
        }

        if (!commit.success) {
            // 提交失败时统一转成 FAILED_COMMIT，保持失败来源可追踪。
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

        // 提交成功且最终回调完成后，把会话收口为成功态。
        sessionStore.updateStatus(
            sessionId = sessionId,
            status = InstallSessionStatus.CALLBACK_SUCCESS,
            progress = 100,
        )
        onEvent(InstallEvent.Progress(sessionId, 100))
        onEvent(InstallEvent.Success(request.targetVersion))
    }

    /** 持久化安装会话记录。 */
    private fun persistSession(record: InstallSessionRecord) {
        sessionStore.save(record)
    }
}
