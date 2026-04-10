package com.nio.appstore.domain.state

enum class DownloadStatus {
    /** 无下载任务。 */
    IDLE,
    /** 已进入等待下载状态。 */
    WAITING,
    /** 下载执行中。 */
    RUNNING,
    /** 下载被用户暂停。 */
    PAUSED,
    /** 下载已完成。 */
    COMPLETED,
    /** 下载失败。 */
    FAILED,
    /** 下载已取消。 */
    CANCELED,
}

enum class InstallStatus {
    /** 当前未安装。 */
    NOT_INSTALLED,
    /** 已进入等待安装状态。 */
    WAITING,
    /** 正在等待系统确认安装。 */
    PENDING_USER_ACTION,
    /** 安装执行中。 */
    INSTALLING,
    /** 已安装成功。 */
    INSTALLED,
    /** 安装失败。 */
    FAILED,
}

enum class UpgradeStatus {
    /** 当前没有升级动作。 */
    NONE,
    /** 已发现可升级版本。 */
    AVAILABLE,
    /** 升级执行中。 */
    UPGRADING,
    /** 升级成功。 */
    SUCCESS,
    /** 升级失败。 */
    FAILED,
}

enum class PrimaryAction {
    /** 发起下载。 */
    DOWNLOAD,
    /** 暂停下载。 */
    PAUSE,
    /** 恢复下载。 */
    RESUME,
    /** 发起安装。 */
    INSTALL,
    /** 打开应用。 */
    OPEN,
    /** 发起升级。 */
    UPGRADE,
    /** 重试下载。 */
    RETRY_DOWNLOAD,
    /** 重试安装。 */
    RETRY_INSTALL,
    /** 当前无可点击主动作。 */
    DISABLED,
}
