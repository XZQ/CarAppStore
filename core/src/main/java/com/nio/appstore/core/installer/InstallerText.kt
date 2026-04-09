package com.nio.appstore.core.installer

object InstallerText {
    const val FAILURE_APK_MISSING = "安装包不存在"
    const val FAILURE_APK_INVALID = "安装包无效"
    const val FAILURE_POLICY_BLOCKED = "安装受限"
    const val FAILURE_SESSION_CREATE_FAILED = "安装会话创建失败"
    const val FAILURE_SESSION_WRITE_FAILED = "安装包写入失败"
    const val FAILURE_SESSION_COMMIT_FAILED = "安装会话提交失败"
    const val FAILURE_INSTALL_INTERRUPTED = "安装中断"
    const val FAILURE_UNKNOWN = "未知安装错误"

    const val SESSION_COMMIT_SUCCESS = "安装会话提交成功"
    const val SESSION_INTERRUPTED_RECOVERABLE = "安装会话在上次退出时中断，可重试安装"
    const val NO_AVAILABLE_INSTALLER = "无可用安装器"

    const val STATUS_FAILED_PREFIX = "FAILED_"
}
