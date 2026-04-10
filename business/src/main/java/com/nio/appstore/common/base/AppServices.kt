package com.nio.appstore.common.base

import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.core.installer.InstallUserActionDispatcher
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager

/**
 * AppServices 定义 feature/common 层需要访问的最小全局服务集合。
 *
 * 它保持在 common 包名下，避免 BaseFragment 直接依赖 app 模块里的具体 AppContainer 类型。
 */
interface AppServices {
    val appManager: AppManager
    val stateCenter: StateCenter
    val downloadManager: DownloadManager
    val installManager: InstallManager
    val upgradeManager: UpgradeManager
    val policyCenter: PolicyCenter
    val installSessionStore: InstallSessionStore
    val installUserActionDispatcher: InstallUserActionDispatcher
}
