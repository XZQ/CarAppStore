package com.nio.appstore.core.installer

object InstallSessionStatus {
    /** 安装会话已创建，但安装包内容尚未写入。 */
    const val CREATED = "CREATED"
    /** 安装包内容已经写入平台安装会话。 */
    const val WRITTEN = "WRITTEN"
    /** 已请求提交安装会话，正在等待回调确认。 */
    const val COMMITTED = "COMMITTED"
    /** 系统要求用户确认安装，当前等待壳层拉起确认页。 */
    const val PENDING_USER_ACTION = "PENDING_USER_ACTION"
    /** 平台回调已确认安装会话成功完成。 */
    const val CALLBACK_SUCCESS = "CALLBACK_SUCCESS"
    /** 进程重启后发现可恢复安装会话，并已标记为可重试处理。 */
    const val RECOVERED_INTERRUPTED = "RECOVERED_INTERRUPTED"

    /** 安装会话创建失败。 */
    const val FAILED_CREATE = "FAILED_CREATE"
    /** 安装包写入安装会话失败。 */
    const val FAILED_WRITE = "FAILED_WRITE"
    /** 安装会话提交失败。 */
    const val FAILED_COMMIT = "FAILED_COMMIT"

    fun isRecoverable(status: String): Boolean {
        return status == CREATED || status == WRITTEN || status == COMMITTED || status == PENDING_USER_ACTION
    }

    fun isFailed(status: String): Boolean {
        return status.startsWith(InstallerText.STATUS_FAILED_PREFIX)
    }

    fun isRetryable(status: String): Boolean {
        return status == RECOVERED_INTERRUPTED || isFailed(status)
    }
}
