package com.nio.appstore.app

import android.os.Bundle
import android.widget.Button
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nio.appstore.R
import com.nio.appstore.common.base.AppContainerProvider
import com.nio.appstore.databinding.ActivityMainBinding
import com.nio.appstore.feature.detail.DetailFragment
import com.nio.appstore.feature.debug.DeveloperSettingsFragment
import com.nio.appstore.feature.downloadmanager.DownloadManagerFragment
import com.nio.appstore.feature.home.HomeFragment
import com.nio.appstore.feature.installcenter.InstallCenterFragment
import com.nio.appstore.feature.myapp.MyAppFragment
import com.nio.appstore.feature.search.SearchFragment
import com.nio.appstore.feature.upgrade.UpgradeFragment
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
class MainActivity : AppCompatActivity(), com.nio.appstore.common.navigation.MainNavigator {

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
        navigateTo(
            fragment = SearchFragment.newInstance(),
            titleRes = R.string.title_search,
            selectedButton = binding.btnNavSearch,
        )
    }

    /** 打开下载中心。 */
    override fun openDownloadManager() {
        navigateTo(
            fragment = DownloadManagerFragment.newInstance(),
            titleRes = R.string.title_download_manager,
            selectedButton = binding.btnNavDownload,
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
        binding.btnNavSearch.setOnClickListener { openSearch() }
        binding.btnNavDownload.setOnClickListener { openDownloadManager() }
        binding.btnNavUpgrade.setOnClickListener { openUpgradeManager() }
        binding.btnNavInstall.setOnClickListener { openInstallManager() }
        binding.btnNavMyApps.setOnClickListener { openMyApps() }
        binding.btnNavDebug.setOnClickListener { openDeveloperSettings() }
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
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .apply {
                if (addToBackStack) {
                    addToBackStack(null)
                }
            }
            .commit()

        updateTitle(getString(titleRes))
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
            binding.btnNavMyApps,
            binding.btnNavDebug,
        )
        buttons.forEach { button ->
            button.isSelected = button == selected
        }
    }
}
