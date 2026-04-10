package com.nio.appstore.feature.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager

class UpgradeViewModelFactory(
    /** 提供升级中心聚合任务数据。 */
    private val appManager: AppManager,
    /** 提供升级任务实时状态。 */
    private val stateCenter: StateCenter,
    /** 负责升级中心的升级动作。 */
    private val upgradeManager: UpgradeManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建升级中心 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UpgradeViewModel(appManager, stateCenter, upgradeManager) as T
    }
}
