package com.xzq.appstore.app

import android.os.Bundle
import android.widget.Button
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
import com.xzq.appstore.feature.detail.DetailFragment
import com.xzq.appstore.feature.debug.DeveloperSettingsFragment
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
 * 它的职责尽量控制在：
 * 1. 承载顶部导航与 Fragment 容器；
 * 2. 实现 MainNavigator；
 * 3. 统一处理页面切换时的标题与导航按钮态。
 *
 * M4 阶段把重复的 FragmentTransaction 收敛到了 navigateTo，
 * 这样壳层代码更像“统一装配 + 导航控制”，而不是到处散着重复事务代码。
 */
class MainActivity : AppCompatActivity(), com.xzq.appstore.common.navigation.MainNavigator {

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

    private fun openCatalog(page: CatalogPage, selectedButton: Button?) {
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
            selectedButton = binding.btnNavDesktopDownload,
        )
    }

    /** 打开升级中心。 */
    override fun openUpgradeManager() {
        navigateTo(
            fragment = UpgradeFragment.newInstance(),
            titleRes = R.string.title_upgrade,
            selectedButton = binding.btnNavUpgrade,
        )
    }

    /** 打开安装中心。 */
    override fun openInstallManager() {
        navigateTo(
            fragment = InstallCenterFragment.newInstance(),
            titleRes = R.string.title_install_manager,
            selectedButton = binding.btnNavInstall,
        )
    }

    /** 打开开发设置页。 */
    override fun openDeveloperSettings() {
        navigateTo(
            fragment = DeveloperSettingsFragment.newInstance(),
            titleRes = R.string.title_developer_settings,
            selectedButton = binding.btnNavDebug,
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
     * 这样 onCreate 不会被一长串 setOnClickListener 淹没，
     * 也更符合“壳层做装配”的角色。
     */
    private fun bindNavigationClicks() {
        binding.btnNavHome.setOnClickListener { openHome() }
        binding.btnNavSearch.setOnClickListener { openGame() }
        binding.btnNavDownload.setOnClickListener { openSearch() }
        binding.btnNavUpgrade.setOnClickListener { openCatalog(CatalogPage.Category, binding.btnNavUpgrade) }
        binding.btnNavInstall.setOnClickListener { openCatalog(CatalogPage.Rank, binding.btnNavInstall) }
        binding.btnNavEssential.setOnClickListener { openCatalog(CatalogPage.Essential, binding.btnNavEssential) }
        binding.btnNavMyApps.setOnClickListener { openMyApps() }
        binding.btnNavDebug.setOnClickListener { openCatalog(CatalogPage.Activity, binding.btnNavDebug) }
        binding.btnNavDesktopDownload.setOnClickListener { openDownloadManager() }
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
        val downloadStats = appServices.appManager.getDownloadTaskStats()
        val installStats = appServices.appManager.getInstallTaskStats()
        val upgradeStats = appServices.appManager.getUpgradeTaskStats()
        binding.tvDesktopSummaryTitle.text = "任务总览"
        binding.tvDesktopSummaryBody.text = buildTaskSummaryBody(downloadStats, installStats, upgradeStats)
    }

    private fun buildTaskSummaryBody(
        downloadStats: TaskCenterStats,
        installStats: TaskCenterStats,
        upgradeStats: TaskCenterStats,
    ): String {
        return listOf(
            formatTaskStats("下载", downloadStats),
            formatTaskStats("安装", installStats),
            formatTaskStats("升级", upgradeStats),
        ).joinToString("\n")
    }

    private fun formatTaskStats(label: String, stats: TaskCenterStats): String {
        return "$label：运行 ${stats.activeCount}，待处理 ${stats.pendingCount}，失败 ${stats.failedCount}，完成 ${stats.completedCount}"
    }

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
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .apply {
                if (addToBackStack) {
                    addToBackStack(null)
                }
            }
            .commit()

        updateTitle(title)
        selectNav(selectedButton)
    }

    /**
     * 统一处理导航按钮选中态。
     *
     * 壳层只维护一级导航按钮的显隐与选中，不感知业务状态。
     */
    private fun selectNav(selected: Button?) {
        val buttons = listOf(
            binding.btnNavHome,
            binding.btnNavSearch,
            binding.btnNavDownload,
            binding.btnNavUpgrade,
            binding.btnNavInstall,
            binding.btnNavEssential,
            binding.btnNavMyApps,
            binding.btnNavDebug,
            binding.btnNavDesktopDownload,
        )
        buttons.forEach { button ->
            button.isSelected = button == selected
        }
    }
}
