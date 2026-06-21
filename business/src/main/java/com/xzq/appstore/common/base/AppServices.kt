package com.xzq.appstore.common.base

import com.xzq.appstore.core.installer.InstallSessionStore
import com.xzq.appstore.core.installer.InstallUserActionDispatcher
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.upgrade.UpgradeManager

/**
 * AppServices 定义 feature/common 层需要访问的最小全局服务集合。
 *
 * 它保持在 common 包名下，避免 BaseFragment 直接依赖 app 模块里的具体 AppContainer 类型。
 */
interface AppServices {
    /** 应用管理聚合入口，向页面提供列表、详情和任务中心视图数据。 */
    val appManager: AppManager
    /** 全局状态中心，维护每个应用的运行态。 */
    val stateCenter: StateCenter
    /** 下载业务编排入口。 */
    val downloadManager: DownloadManager
    /** 安装业务编排入口。 */
    val installManager: InstallManager
    /** 升级业务编排入口。 */
    val upgradeManager: UpgradeManager
    /** 策略中心入口，负责下载/安装/升级前置校验。 */
    val policyCenter: PolicyCenter
    /** 安装会话存储，供安装中心和安装器共享。 */
    val installSessionStore: InstallSessionStore
    /** 安装确认动作分发器，供壳层统一拉起系统确认页。 */
    val installUserActionDispatcher: InstallUserActionDispatcher
    /** 本地事件打点入口，供壳层和业务动作统一记录曝光、点击和结果。 */
    val eventTracker: EventTracker
}
