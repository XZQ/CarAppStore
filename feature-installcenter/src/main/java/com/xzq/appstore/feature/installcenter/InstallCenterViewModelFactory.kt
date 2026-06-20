package com.xzq.appstore.feature.installcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xzq.appstore.core.installer.InstallSessionStore
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.upgrade.UpgradeManager

class InstallCenterViewModelFactory(
    /** 提供安装中心聚合任务数据。 */
    private val appManager: AppManager,
    /** 提供安装任务实时状态。 */
    private val stateCenter: StateCenter,
    /** 负责安装中心的安装动作。 */
    private val installManager: InstallManager,
    /** 负责安装后的升级状态检查。 */
    private val upgradeManager: UpgradeManager,
    /** 提供安装会话列表与恢复信息。 */
    private val installSessionStore: InstallSessionStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建安装中心 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InstallCenterViewModel(appManager, stateCenter, installManager, upgradeManager, installSessionStore) as T
    }
}
