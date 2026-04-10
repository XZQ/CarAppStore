package com.nio.appstore.domain.state

enum class DownloadStatus {
    IDLE,
    WAITING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED,
}

enum class InstallStatus {
    NOT_INSTALLED,
    WAITING,
    PENDING_USER_ACTION,
    INSTALLING,
    INSTALLED,
    FAILED,
}

enum class UpgradeStatus {
    NONE,
    AVAILABLE,
    UPGRADING,
    SUCCESS,
    FAILED,
}

enum class PrimaryAction {
    DOWNLOAD,
    PAUSE,
    RESUME,
    INSTALL,
    OPEN,
    UPGRADE,
    RETRY_DOWNLOAD,
    RETRY_INSTALL,
    DISABLED,
}
