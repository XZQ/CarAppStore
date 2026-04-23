package com.nio.appstore.core.installer

/**
 * InstallerText 收敛安装器内部使用的失败文案和状态前缀。
 */
object InstallerText {
    /** 安装包不存在失败文案。 */
    const val FAILURE_APK_MISSING = "安装包不存在"
    const val FAILURE_APK_INVALID = "安装包无效"
    const val FAILURE_POLICY_BLOCKED = "安装受限"
    const val FAILURE_SESSION_CREATE_FAILED = "安装会话创建失败"
    const val FAILURE_SESSION_WRITE_FAILED = "安装包写入失败"
    const val FAILURE_SESSION_COMMIT_FAILED = "安装会话提交失败"
    const val FAILURE_SESSION_NOT_SUPPORTED = "当前环境不支持系统安装会话"
    const val FAILURE_INSTALL_INTERRUPTED = "安装中断"
    const val FAILURE_UNKNOWN = "未知安装错误"

    const val SESSION_COMMIT_SUCCESS = "安装会话提交成功"
    const val SESSION_COMMIT_TIMEOUT = "安装会话提交超时"
    const val SESSION_PENDING_USER_ACTION = "安装会话等待系统确认"
    const val SESSION_PENDING_USER_ACTION_MISSING_INTENT = "安装会话需要系统确认，但确认入口不可用"
    const val SESSION_INTERRUPTED_RECOVERABLE = "安装会话在上次退出时中断，可重试安装"
    const val SESSION_NOT_SUPPORTED = "当前环境不支持系统安装会话"

    /** 失败状态统一前缀。 */
    const val STATUS_FAILED_PREFIX = "FAILED_"

    /** 提交失败与底层详情拼接格式。 */
    private const val FAILURE_DETAIL_FORMAT = "%s：%s"

    /** 提交失败与平台状态码拼接格式。 */
    private const val FAILURE_STATUS_FORMAT = "%s（status=%d）"

    /**
     * 生成带底层详情的安装会话提交失败文案。
     *
     * @param detail 底层异常或平台返回的失败详情
     */
    fun sessionCommitFailureWithDetail(detail: String): String {
        return FAILURE_DETAIL_FORMAT.format(FAILURE_SESSION_COMMIT_FAILED, detail)
    }

    /**
     * 生成带平台状态码的安装会话提交失败文案。
     *
     * @param status PackageInstaller 返回的状态码
     */
    fun sessionCommitFailureWithStatus(status: Int): String {
        return FAILURE_STATUS_FORMAT.format(FAILURE_SESSION_COMMIT_FAILED, status)
    }
}
