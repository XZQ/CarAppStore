package com.xzq.appstore.core.installer

import android.content.Context

/**
 * InstallSessionReconciliationResult 描述一次冷启动安装会话对账结果。
 */
data class InstallSessionReconciliationResult(
    /** 已根据 PackageManager 事实补记为安装成功的会话数。 */
    val completedSessionCount: Int,
    /** 已转为可重试中断态的会话数。 */
    val interruptedSessionCount: Int,
    /** 已主动放弃的遗留平台会话数。 */
    val abandonedPlatformSessionCount: Int,
)

/**
 * OwnedInstallSessionGateway 封装本应用拥有的平台安装会话查询与清理能力。
 */
interface OwnedInstallSessionGateway {
    /** 返回当前仍由本应用持有的平台安装会话 ID。 */
    fun ownedSessionIds(): Set<Int>

    /** 放弃指定遗留平台会话，成功时返回 true。 */
    fun abandonSession(sessionId: Int): Boolean
}

/**
 * AndroidOwnedInstallSessionGateway 通过系统 PackageInstaller 管理本应用的平台会话。
 */
class AndroidOwnedInstallSessionGateway(context: Context) : OwnedInstallSessionGateway {
    // 系统安装会话入口，仅持有应用级 PackageManager 对象。
    private val systemPackageInstaller = context.applicationContext.packageManager.packageInstaller

    /** 查询当前应用仍持有的平台安装会话。 */
    override fun ownedSessionIds(): Set<Int> = runCatching { systemPackageInstaller.mySessions.mapTo(linkedSetOf()) { it.sessionId } }.getOrDefault(emptySet())

    /** 放弃进程死亡后无法继续接收原动态广播回调的平台会话。 */
    override fun abandonSession(sessionId: Int): Boolean = runCatching {
        systemPackageInstaller.abandonSession(sessionId)
        true
    }.getOrDefault(false)
}

/**
 * InstallSessionReconciler 在冷启动时把本地会话、平台 Session 与已安装事实重新对齐。
 *
 * 已完成安装但丢失回调的会话会补记成功；其余可恢复会话若仍残留在系统中则先放弃，
 * 再统一标记为可重试中断态，避免页面永久停留在等待确认或安装中。
 */
class InstallSessionReconciler(
    /** 本地安装会话真相存储。 */
    private val sessionStore: InstallSessionStore,
    /** 平台安装会话查询与清理入口。 */
    private val platformSessionGateway: OwnedInstallSessionGateway,
    /** PackageManager 已安装包事实查询入口。 */
    private val installedPackageInspector: InstalledPackageInspector,
) {
    /** 执行一次冷启动安装会话对账。 */
    fun reconcile(): InstallSessionReconciliationResult {
        val ownedSessionIds = platformSessionGateway.ownedSessionIds()
        var completedSessionCount = 0
        var interruptedSessionCount = 0
        var abandonedPlatformSessionCount = 0
        sessionStore.getRecoverableSessions().forEach { record ->
            if (isInstalledTarget(record)) {
                sessionStore.updateStatus(record.sessionId, InstallSessionStatus.CALLBACK_SUCCESS, 100)
                completedSessionCount += 1
                return@forEach
            }
            if (record.sessionId in ownedSessionIds && platformSessionGateway.abandonSession(record.sessionId)) {
                abandonedPlatformSessionCount += 1
            }
            sessionStore.updateStatus(
                sessionId = record.sessionId,
                status = InstallSessionStatus.RECOVERED_INTERRUPTED,
                progress = record.progress,
                failureCode = record.failureCode ?: InstallFailureCode.INSTALL_INTERRUPTED.name,
                failureMessage = record.failureMessage ?: InstallerText.SESSION_INTERRUPTED_RECOVERABLE,
            )
            interruptedSessionCount += 1
        }
        return InstallSessionReconciliationResult(completedSessionCount, interruptedSessionCount, abandonedPlatformSessionCount)
    }

    /** 判断目标包与目标版本是否已经成为系统安装事实。 */
    private fun isInstalledTarget(record: InstallSessionRecord): Boolean {
        if (record.packageName.isBlank() || record.targetVersion.isBlank()) {
            return false
        }
        val installed = installedPackageInspector.getInstalledIdentity(record.packageName) ?: return false
        return installed.packageName == record.packageName && installed.versionName == record.targetVersion
    }
}
