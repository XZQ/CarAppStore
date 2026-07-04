package com.xzq.appstore.app

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
import com.xzq.appstore.R
import com.xzq.appstore.common.base.AppContainerProvider
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
import kotlinx.coroutines.launch

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
class MainActivity :
    AppCompatActivity(),
    com.xzq.appstore.common.navigation.MainNavigator {
    /** 主页面的 ViewBinding。 */
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

    /** 打开首页。 */
    override fun openHome() {
        navigateTo(
            fragment = HomeFragment.newInstance(),
            titleRes = R.string.title_home,
            selectedButton = binding.btnNavHome,
            addToBackStack = false,
        )
    }

    /** 打开搜索页。 */
    override fun openSearch() {
        openCatalog(CatalogPage.Software, binding.btnNavDownload)
    }

    private fun openGame() {
        openCatalog(CatalogPage.Game, binding.btnNavSearch)
    }

    private fun openCatalog(
        page: CatalogPage,
        selectedButton: Button?,
    ) {
        navigateTo(
            fragment = SearchFragment.newInstance(page),
            title = page.title,
            selectedButton = selectedButton,
        )
    }

    /** 打开下载中心。 */
    override fun openDownloadManager() {
        navigateTo(
            fragment = DownloadManagerFragment.newInstance(),
            titleRes = R.string.title_download_manager,
            selectedButton = optionalButton(R.id.btnNavDesktopDownload),
        )
    }

    /** 打开升级中心。 */
    override fun openUpgradeManager() {
        navigateTo(
            fragment = UpgradeFragment.newInstance(),
            titleRes = R.string.title_upgrade,
            selectedButton = optionalButton(R.id.btnNavUpgrade),
        )
    }

    /** 打开安装中心。 */
    override fun openInstallManager() {
        navigateTo(
            fragment = InstallCenterFragment.newInstance(),
            titleRes = R.string.title_install_manager,
            selectedButton = optionalButton(R.id.btnNavInstall),
        )
    }

    /** 打开开发设置页。 */
    override fun openDeveloperSettings() {
        navigateTo(
            fragment = DeveloperSettingsFragment.newInstance(),
            titleRes = R.string.title_developer_settings,
            selectedButton = optionalButton(R.id.btnNavDebug),
        )
    }

    /** 打开应用详情页。 */
    override fun openDetail(appId: String) {
        navigateTo(
            fragment = DetailFragment.newInstance(appId),
            titleRes = R.string.title_detail,
            selectedButton = null,
        )
    }

    /** 打开“我的应用”页面。 */
    override fun openMyApps() {
        navigateTo(
            fragment = MyAppFragment.newInstance(),
            titleRes = R.string.title_my_apps,
            selectedButton = binding.btnNavMyApps,
        )
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
        optionalButton(R.id.btnNavDebug)?.setOnClickListener {
            openCatalog(CatalogPage.Activity, optionalButton(R.id.btnNavDebug))
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
                appServices.stateCenter.observeAll().collect {
                    updateTaskSummary()
                }
            }
        }
    }

    private suspend fun updateTaskSummary() {
        val titleView = optionalTextView(R.id.tvDesktopSummaryTitle) ?: return
        val bodyView = optionalTextView(R.id.tvDesktopSummaryBody) ?: return
        val downloadStats = appServices.appManager.getDownloadTaskStats()
        val installStats = appServices.appManager.getInstallTaskStats()
        val upgradeStats = appServices.appManager.getUpgradeTaskStats()
        titleView.text = getString(com.xzq.appstore.common.R.string.ui_task_summary)
        bodyView.text = buildTaskSummaryBody(downloadStats, installStats, upgradeStats)
    }

    private fun buildTaskSummaryBody(
        downloadStats: TaskCenterStats,
        installStats: TaskCenterStats,
        upgradeStats: TaskCenterStats,
    ): String =
        listOf(
            formatTaskStats(getString(R.string.task_label_download), downloadStats),
            formatTaskStats(getString(R.string.task_label_install), installStats),
            formatTaskStats(getString(R.string.task_label_upgrade), upgradeStats),
        ).joinToString("\n")

    private fun formatTaskStats(
        label: String,
        stats: TaskCenterStats,
    ): String =
        getString(
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
     * @param selectedButton 当前应高亮的导航按钮，可为空
     * @param addToBackStack 是否加入返回栈
     */
    private fun navigateTo(
        fragment: Fragment,
        @StringRes titleRes: Int,
        selectedButton: Button?,
        addToBackStack: Boolean = true,
    ) {
        navigateTo(
            fragment = fragment,
            title = getString(titleRes),
            selectedButton = selectedButton,
            addToBackStack = addToBackStack,
        )
    }

    private fun navigateTo(
        fragment: Fragment,
        title: String,
        selectedButton: Button?,
        addToBackStack: Boolean = true,
    ) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .apply {
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
        val ids =
            listOf(
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

    /** 按 id 查找可选 Button，桌面专属按钮在 phone/sw600dp 布局中不存在时返回 null。 */
    private fun optionalButton(id: Int): Button? = binding.root.findViewById<View?>(id) as? Button

    /** 按 id 查找可选 TextView，桌面侧栏标题在 phone/sw600dp 布局中不存在时返回 null。 */
    private fun optionalTextView(id: Int): TextView? = binding.root.findViewById<View?>(id) as? TextView
}
