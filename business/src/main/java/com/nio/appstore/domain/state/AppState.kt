package com.nio.appstore.domain.state

data class AppState(
    val appId: String,
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,
    val installStatus: InstallStatus = InstallStatus.NOT_INSTALLED,
    val upgradeStatus: UpgradeStatus = UpgradeStatus.NONE,
    val progress: Int = 0,
    val localApkPath: String? = null,
    val installedVersion: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val primaryAction: PrimaryAction = PrimaryAction.DOWNLOAD,
    val statusText: String = "未安装",
)
