package com.nio.appstore.domain.state

import com.nio.appstore.data.model.ModelText

/**
 * AppState 是状态中心维护的单应用运行态快照。
 */
data class AppState(
    /** 状态中心使用的稳定应用标识。 */
    val appId: String,
    /** 当前应用对应的下载状态。 */
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,
    /** 当前应用对应的安装状态。 */
    val installStatus: InstallStatus = InstallStatus.NOT_INSTALLED,
    /** 当前应用对应的升级状态。 */
    val upgradeStatus: UpgradeStatus = UpgradeStatus.NONE,
    /** 当前活跃流程对应的进度百分比。 */
    val progress: Int = 0,
    /** 已解析出的本地安装包路径，存在时可用。 */
    val localApkPath: String? = null,
    /** 状态中心记录的已安装版本号。 */
    val installedVersion: String? = null,
    /** 当前状态关联的用户可见错误文案。 */
    val errorMessage: String? = null,
    /** 当前状态关联的稳定错误码。 */
    val errorCode: String? = null,
    /** 当前暴露给界面的主动作。 */
    val primaryAction: PrimaryAction = PrimaryAction.DOWNLOAD,
    /** 在列表和详情等界面展示的状态文案。 */
    val statusText: String = ModelText.STATUS_NOT_INSTALLED,
)
