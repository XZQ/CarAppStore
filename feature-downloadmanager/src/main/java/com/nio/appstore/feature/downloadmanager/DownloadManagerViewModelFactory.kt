package com.nio.appstore.feature.downloadmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.StateCenter

class DownloadManagerViewModelFactory(
    /** 提供下载中心所需的聚合任务数据。 */
    private val appManager: AppManager,
    /** 提供任务实时状态。 */
    private val stateCenter: StateCenter,
    /** 负责下载中心内的下载动作。 */
    private val downloadManager: DownloadManager,
    /** 负责下载中心内的安装动作。 */
    private val installManager: InstallManager,
    /** 提供策略设置与切换能力。 */
    private val policyCenter: PolicyCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建下载中心 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DownloadManagerViewModel(appManager, stateCenter, downloadManager, installManager, policyCenter) as T
    }
}
