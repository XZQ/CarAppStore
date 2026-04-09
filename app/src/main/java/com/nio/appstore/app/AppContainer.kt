package com.nio.appstore.app

import android.content.Context
import com.nio.appstore.common.base.AppServices
import com.nio.appstore.core.downloader.DownloadSourceResolver
import com.nio.appstore.core.downloader.DownloadSourceResolverConfig
import com.nio.appstore.core.downloader.DownloadStore
import com.nio.appstore.core.downloader.RealFileDownloader
import com.nio.appstore.core.downloader.SimulatedFileDownloader
import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.core.installer.RealPackageInstaller
import com.nio.appstore.core.installer.SimulatedPackageInstaller
import com.nio.appstore.core.installer.SystemPackageInstallerSessionAdapter
import com.nio.appstore.core.logger.AppLogger
import com.nio.appstore.core.tracker.EventTracker
import com.nio.appstore.data.datasource.local.AppLocalDataSource
import com.nio.appstore.data.datasource.remote.AppRemoteDataSource
import com.nio.appstore.data.datasource.remote.DownloadSourceCatalog
import com.nio.appstore.data.datasource.system.AppSystemDataSource
import com.nio.appstore.data.downloadenv.DownloadEnvironmentEntry
import com.nio.appstore.data.downloadenv.LocalDownloadEnvironmentProvider
import com.nio.appstore.data.local.store.JsonBackedLocalStoreFacade
import com.nio.appstore.data.local.store.LocalStoreFacade
import com.nio.appstore.data.repository.AppRepository
import com.nio.appstore.data.repository.FakeAppRepository
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.appmanager.DefaultAppManager
import com.nio.appstore.domain.download.DefaultDownloadManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.DefaultInstallManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.DefaultPolicyCenter
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.DefaultStateCenter
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.DefaultUpgradeManager
import com.nio.appstore.domain.upgrade.UpgradeManager

/**
 * AppContainer 是当前 app 壳层的主装配入口。
 *
 * 它的职责是：
 * 1. 创建跨页面共享的核心依赖；
 * 2. 把 data / core / business 中的实现装配起来；
 * 3. 向 UI 壳层提供稳定的对象入口。
 *
 * 当前 M4 阶段先不把它继续拆成更细的 bootstrap/assembly 对象，
 * 但已经把文件路径等细节收敛到 AppStoragePaths，减少壳层中的散乱引用。
 */
class AppContainer(context: Context) : AppServices {
    private val appContext = context.applicationContext

    /** 统一管理 app 壳层会用到的本地文件路径。 */
    private val storagePaths: AppStoragePaths by lazy { AppStoragePaths(appContext) }

    /** 日志器，当前作为全局基础能力在壳层统一创建。 */
    val logger: AppLogger by lazy { AppLogger() }

    /** 打点器，供下载、安装、升级等链路复用。 */
    val tracker: EventTracker by lazy { EventTracker() }

    /** 统一数据层 facade，当前默认采用结构化 JSON 落盘实现。 */
    val localStoreFacade: LocalStoreFacade by lazy {
        JsonBackedLocalStoreFacade(storagePaths.structuredLocalStoreFile)
    }

    /** 下载环境配置入口，当前从 facade 优先读取，并保留兼容 fallback。 */
    val downloadEnvironmentProvider: LocalDownloadEnvironmentProvider by lazy {
        LocalDownloadEnvironmentProvider(appContext, localStoreFacade)
    }

    private val downloadEnvironmentEntry by lazy {
        DownloadEnvironmentEntry(downloadEnvironmentProvider)
    }

    private val downloadEnvConfig by lazy {
        downloadEnvironmentEntry.currentConfig()
    }

    /** 远端数据源，根据当前下载环境切换下载源目录。 */
    private val remoteDataSource: AppRemoteDataSource by lazy {
        AppRemoteDataSource(
            sourceCatalog = DownloadSourceCatalog(downloadEnvConfig.environment),
        )
    }

    /** 本地数据源，当前已开始统一接入结构化 facade。 */
    private val localDataSource: AppLocalDataSource by lazy {
        AppLocalDataSource(appContext, localStoreFacade)
    }

    /** 系统数据源，封装系统安装应用、包信息等读取能力。 */
    private val systemDataSource: AppSystemDataSource by lazy {
        AppSystemDataSource(appContext)
    }

    /** Repository 装配入口，负责聚合远端、本地、系统三类数据。 */
    val repository: AppRepository by lazy {
        FakeAppRepository(remoteDataSource, localDataSource, systemDataSource)
    }

    /** 全局状态中心。 */
    override val stateCenter: StateCenter by lazy { DefaultStateCenter() }

    /** 全局策略中心。 */
    override val policyCenter: PolicyCenter by lazy {
        DefaultPolicyCenter(appContext, localDataSource)
    }

    /** 下载执行器，当前优先走真实下载器，必要时回退模拟实现。 */
    private val fileDownloader by lazy {
        RealFileDownloader(
            store = DownloadStore(storagePaths.downloadsDir),
            sourceResolver = DownloadSourceResolver(
                DownloadSourceResolverConfig(
                    defaultSourcePolicy = downloadEnvConfig.defaultSourcePolicy,
                    allowMockSource = downloadEnvConfig.allowMockSource,
                    allowDirectHttp = downloadEnvConfig.allowDirectHttp,
                )
            ),
            fallbackDownloader = SimulatedFileDownloader(),
        )
    }

    /** 安装会话存储，当前同时服务安装器与安装中心。 */
    override val installSessionStore: InstallSessionStore by lazy {
        InstallSessionStore(storagePaths.installSessionsFile)
    }

    /** 安装执行器，当前优先走真实安装器骨架。 */
    private val packageInstaller by lazy {
        RealPackageInstaller(
            context = appContext,
            sessionAdapter = SystemPackageInstallerSessionAdapter(appContext),
            sessionStore = installSessionStore,
            fallbackInstaller = SimulatedPackageInstaller(),
        )
    }

    /** 下载业务编排入口。 */
    override val downloadManager: DownloadManager by lazy {
        DefaultDownloadManager(
            repository = repository,
            stateCenter = stateCenter,
            policyCenter = policyCenter,
            fileDownloader = fileDownloader,
            logger = logger,
            tracker = tracker,
        )
    }

    /** 安装业务编排入口。 */
    override val installManager: InstallManager by lazy {
        DefaultInstallManager(
            repository = repository,
            stateCenter = stateCenter,
            policyCenter = policyCenter,
            packageInstaller = packageInstaller,
            logger = logger,
            tracker = tracker,
        )
    }

    /** 升级业务编排入口。 */
    override val upgradeManager: UpgradeManager by lazy {
        DefaultUpgradeManager(
            repository = repository,
            stateCenter = stateCenter,
            policyCenter = policyCenter,
            downloadManager = downloadManager,
            installManager = installManager,
            logger = logger,
            tracker = tracker,
        )
    }

    /** 应用管理聚合入口，给首页、详情页、我的应用、各任务中心使用。 */
    override val appManager: AppManager by lazy {
        DefaultAppManager(repository, stateCenter, installSessionStore)
    }

    init {
        // 冷启动时先修正上次可能中断的安装会话，再启动下载链的恢复。
        installSessionStore.markRecoveredSessionsAsInterrupted()
        downloadManager
    }
}
