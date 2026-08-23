package com.xzq.appstore.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xzq.appstore.BuildConfig
import com.xzq.appstore.R
import com.xzq.appstore.common.base.AppContainerProvider
import com.xzq.appstore.common.navigation.MainNavigator
import com.xzq.appstore.data.model.TaskCenterStats
import com.xzq.appstore.databinding.ActivityMainBinding
import com.xzq.appstore.feature.debug.DeveloperSettingsFragment
import com.xzq.appstore.feature.detail.DetailFragment
import com.xzq.appstore.feature.downloadmanager.DownloadManagerFragment
import com.xzq.appstore.feature.home.HomeFragment
import com.xzq.appstore.feature.installcenter.InstallCenterFragment
import com.xzq.appstore.feature.myapp.MyAppFragment
import com.xzq.appstore.feature.search.CatalogPage
import com.xzq.appstore.feature.search.SearchFragment
import com.xzq.appstore.feature.upgrade.UpgradeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xzq.appstore.common.R as CommonR

/**
 * MainActivity 是当前 app 壳层的主页面。
 *
 * 多端契约：
 * - phone / sw600dp：只有 bottomNav 中的 4 个一级按钮（Home / Search / Download / MyApps）。
 * - sw900dp-land：完整 navigationRail + 右侧 desktopSidePanel，附带
 *   Upgrade/Install/Essential/Debug/DesktopDownload 与 tvDesktopSummary* 视图。
 *
 * 因此 tvDesktopSummaryTitle/Body 与 btnNavUpgrade/Install/Essential/Debug/DesktopDownload
 * 只在桌面壳层真实存在；本类用 [optionalButton] / [optionalTextView] 做 nullable 查找，
 * 避免在 phone 布局里塞 0×0 占位视图（旧实现会导致按钮被设点击事件但用户看不见）。
 */
class MainActivity : AppCompatActivity(), MainNavigator {
    private lateinit var binding: ActivityMainBinding

    /** 从应用壳层获取的共享服务入口。 */
    private val appServices get() = (applicationContext as AppContainerProvider).appServices

    /** 初始化主页面、导航按钮和安装确认监听。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindNavigationClicks()
        observeInstallUserActions()
        observeTaskSummaryStats()

        if (savedInstanceState == null) {
            openHome()
        }
    }

    /** 更新顶部标题栏文案。 */
    override fun updateTitle(title: String) {
        binding.tvTitle.text = title
    }

    /** 以新的 AppContainer 重启根任务，确保页面 ViewModel 不再持有旧环境依赖。 */
    override fun restartApplication() {
        (application as App).reloadAppContainer()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finish()
    }

    /** 打开首页。 */
    override fun openHome() {
        navigateTo(fragment = HomeFragment.newInstance(), titleRes = R.string.title_home, tag = TAG_HOME, selectedButton = binding.btnNavHome, addToBackStack = false)
    }

    /** 打开搜索页。 */
    override fun openSearch() {
        openCatalog(CatalogPage.Software, binding.btnNavDownload)
    }

    private fun openGame() {
        openCatalog(CatalogPage.Game, binding.btnNavSearch)
    }

    private fun openCatalog(page: CatalogPage, selectedButton: Button?) {
        navigateTo(fragment = SearchFragment.newInstance(page), title = page.title, tag = "${TAG_CATALOG}:${page.name}", selectedButton = selectedButton)
    }

    /** 打开下载中心。 */
    override fun openDownloadManager() {
        navigateTo(
            fragment = DownloadManagerFragment.newInstance(),
            titleRes = R.string.title_download_manager,
            tag = TAG_DOWNLOAD,
            selectedButton = optionalButton(R.id.btnNavDesktopDownload),
        )
    }

    /** 打开升级中心。 */
    override fun openUpgradeManager() {
        navigateTo(fragment = UpgradeFragment.newInstance(), titleRes = R.string.title_upgrade, tag = TAG_UPGRADE, selectedButton = optionalButton(R.id.btnNavUpgrade))
    }

    /** 打开安装中心。 */
    override fun openInstallManager() {
        navigateTo(
            fragment = InstallCenterFragment.newInstance(),
            titleRes = R.string.title_install_manager,
            tag = TAG_INSTALL,
            selectedButton = optionalButton(R.id.btnNavInstall),
        )
    }

    /** 打开开发设置页。 */
    override fun openDeveloperSettings() {
        navigateTo(
            fragment = DeveloperSettingsFragment.newInstance(),
            titleRes = R.string.title_developer_settings,
            tag = TAG_DEBUG,
            selectedButton = optionalButton(R.id.btnNavDebug),
        )
    }

    /** 打开应用详情页。 */
    override fun openDetail(appId: String) {
        navigateTo(fragment = DetailFragment.newInstance(appId), titleRes = R.string.title_detail, tag = "$TAG_DETAIL:$appId", selectedButton = null)
    }

    /** 打开“我的应用”页面。 */
    override fun openMyApps() {
        navigateTo(fragment = MyAppFragment.newInstance(), titleRes = R.string.title_my_apps, tag = TAG_MY_APPS, selectedButton = binding.btnNavMyApps)
    }

    /**
     * 统一绑定顶部导航点击事件。
     *
     * 桌面专属按钮（Upgrade/Install/Essential/Debug/DesktopDownload）只在桌面壳层存在，
     * 因此通过 [optionalButton] nullable 查找后再绑定，避免 phone 布局被绑定到隐形按钮。
     */
    private fun bindNavigationClicks() {
        binding.btnNavHome.setOnClickListener { openHome() }
        binding.btnNavSearch.setOnClickListener { openGame() }
        binding.btnNavDownload.setOnClickListener { openSearch() }
        binding.btnNavMyApps.setOnClickListener { openMyApps() }
        optionalButton(R.id.btnNavUpgrade)?.setOnClickListener {
            openCatalog(CatalogPage.Category, optionalButton(R.id.btnNavUpgrade))
        }
        optionalButton(R.id.btnNavInstall)?.setOnClickListener {
            openCatalog(CatalogPage.Rank, optionalButton(R.id.btnNavInstall))
        }
        optionalButton(R.id.btnNavEssential)?.setOnClickListener {
            openCatalog(CatalogPage.Essential, optionalButton(R.id.btnNavEssential))
        }
        // 开发者设置仅调试构建可达，避免调试能力（切环境/清缓存/Mock 源）进入 release 包。
        optionalButton(R.id.btnNavDebug)?.let { debugButton ->
            debugButton.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            if (BuildConfig.DEBUG) {
                debugButton.setOnClickListener { openDeveloperSettings() }
            }
        }
        optionalButton(R.id.btnNavDesktopDownload)?.setOnClickListener { openDownloadManager() }
    }

    /**
     * 壳层统一响应系统安装确认请求，避免业务层直接依赖 Activity。
     */
    private fun observeInstallUserActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appServices.installUserActionDispatcher.actions.collect { intent ->
                    startActivity(intent)
                }
            }
        }
    }

    private fun observeTaskSummaryStats() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 下载进度持续推进时也要周期刷新摘要；采样可限频且不会像防抖一样被连续事件长期饿死。
                appServices.stateCenter.observeAll().collect {
                    delay(TASK_SUMMARY_SAMPLE_MS)
                    updateTaskSummary()
                }
            }
        }
    }

    private suspend fun updateTaskSummary() {
        val titleView = optionalTextView(R.id.tvDesktopSummaryTitle) ?: return
        val bodyView = optionalTextView(R.id.tvDesktopSummaryBody) ?: return
        // 统计快照一次加载目录、已装应用和安装会话，统一下放 IO 线程，避免阻塞主线程。
        val snapshot = withContext(Dispatchers.IO) {
            appServices.appManager.getTaskCenterStatsSnapshot()
        }
        titleView.text = getString(CommonR.string.ui_task_summary)
        bodyView.text = buildTaskSummaryBody(snapshot.downloadStats, snapshot.installStats, snapshot.upgradeStats)
    }

    private fun buildTaskSummaryBody(downloadStats: TaskCenterStats, installStats: TaskCenterStats, upgradeStats: TaskCenterStats): String = listOf(
        formatTaskStats(getString(R.string.task_label_download), downloadStats),
        formatTaskStats(getString(R.string.task_label_install), installStats),
        formatTaskStats(getString(R.string.task_label_upgrade), upgradeStats),
    ).joinToString("\n")

    private fun formatTaskStats(label: String, stats: TaskCenterStats): String = getString(
        R.string.task_summary_line_format,
        label,
        stats.activeCount,
        stats.pendingCount,
        stats.failedCount,
        stats.completedCount,
    )

    /**
     * 统一进行页面切换。
     *
     * @param fragment 目标页面
     * @param titleRes 页面标题资源
     * @param tag 目标页面的稳定标识，用于同页去重
     * @param selectedButton 当前应高亮的导航按钮，可为空
     * @param addToBackStack 是否加入返回栈
     */
    private fun navigateTo(fragment: Fragment, @StringRes titleRes: Int, tag: String, selectedButton: Button?, addToBackStack: Boolean = true) {
        navigateTo(fragment = fragment, title = getString(titleRes), tag = tag, selectedButton = selectedButton, addToBackStack = addToBackStack)
    }

    private fun navigateTo(fragment: Fragment, title: String, tag: String, selectedButton: Button?, addToBackStack: Boolean = true) {
        // 连续点击同一目标（同一页面或同一应用的详情页）时跳过，避免返回栈堆叠重复实例。
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current?.tag == tag) {
            updateTitle(title)
            selectNav(selectedButton)
            return
        }
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment, tag).apply {
            if (addToBackStack) {
                addToBackStack(null)
            }
        }.commit()

        updateTitle(title)
        selectNav(selectedButton)
        trackPageView(title)
    }

    private fun trackPageView(title: String) {
        appServices.eventTracker.track("page_view_${title.toEventToken()}")
    }

    private fun String.toEventToken(): String = trim().replace(Regex("\\s+"), "_").ifBlank { "unknown" }

    /**
     * 统一处理导航按钮选中态。
     *
     * 壳层只维护一级导航按钮的显隐与选中，不感知业务状态；桌面专属按钮不存在时跳过。
     */
    private fun selectNav(selected: Button?) {
        val ids = listOf(
            R.id.btnNavHome,
            R.id.btnNavSearch,
            R.id.btnNavDownload,
            R.id.btnNavUpgrade,
            R.id.btnNavInstall,
            R.id.btnNavEssential,
            R.id.btnNavMyApps,
            R.id.btnNavDebug,
            R.id.btnNavDesktopDownload,
        )
        ids.forEach { id ->
            val button = optionalButton(id) ?: return@forEach
            button.isSelected = button == selected
        }
    }

    /** 按 id 查找可选 Button，结果按布局缓存，桌面专属按钮在 phone/sw600dp 布局中不存在时返回 null。 */
    private fun optionalButton(id: Int): Button? = optionalView(id) as? Button

    /** 按 id 查找可选 TextView，结果按布局缓存，桌面侧栏标题在 phone/sw600dp 布局中不存在时返回 null。 */
    private fun optionalTextView(id: Int): TextView? = optionalView(id) as? TextView

    /** 可选视图缓存：布局固定后 findViewById 结果不变，null 结果也需要缓存以避免重复全树遍历。 */
    private fun optionalView(id: Int): View? {
        if (!optionalViewCache.containsKey(id)) {
            optionalViewCache[id] = binding.root.findViewById<View?>(id)
        }
        return optionalViewCache[id]
    }

    /** 可选视图缓存表，仅主线程访问。 */
    private val optionalViewCache = mutableMapOf<Int, View?>()

    private companion object {
        /** 任务摘要刷新采样周期（毫秒），与下载进度节流同量级。 */
        const val TASK_SUMMARY_SAMPLE_MS = 500L

        /** 一级页面与详情页的导航标识，用于同页去重。 */
        const val TAG_HOME = "home"
        const val TAG_CATALOG = "catalog"
        const val TAG_DOWNLOAD = "download"
        const val TAG_UPGRADE = "upgrade"
        const val TAG_INSTALL = "install"
        const val TAG_DEBUG = "debug"
        const val TAG_DETAIL = "detail"
        const val TAG_MY_APPS = "my_apps"
    }
}
