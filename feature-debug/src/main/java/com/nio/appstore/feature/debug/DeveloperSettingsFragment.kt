package com.nio.appstore.feature.debug

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.nio.appstore.common.R
import com.nio.appstore.common.base.BaseFragment
import com.nio.appstore.data.downloadenv.DownloadEnvironmentConfig
import com.nio.appstore.data.downloadenv.DownloadEnvironment
import com.nio.appstore.data.downloadenv.DownloadEnvironmentEntry
import com.nio.appstore.data.downloadenv.LocalDownloadEnvironmentProvider
import com.nio.appstore.feature.debug.databinding.FragmentDeveloperSettingsBinding

class DeveloperSettingsFragment : BaseFragment() {

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentDeveloperSettingsBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!

    /** 下载环境配置读写入口。 */
    private lateinit var environmentProvider: LocalDownloadEnvironmentProvider

    /** 在附着到 Activity 时初始化环境配置入口。 */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        environmentProvider = LocalDownloadEnvironmentProvider(context.applicationContext)
    }

    /** 创建开发设置页视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeveloperSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化页面标题和所有面板。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.ui_developer_settings))
        bindEnvironmentSection()
        bindPolicySignalsSection()
        bindVersionInfoSection()
        bindCacheManagementSection()
    }

    /** 绑定下载环境切换按钮。 */
    private fun bindEnvironmentSection() {
        renderCurrentEnvironment()

        binding.includeEnvironmentPanel.btnEnvDev.setOnClickListener {
            environmentProvider.setCurrentEnvironment(DownloadEnvironment.DEV)
            renderCurrentEnvironment()
        }
        binding.includeEnvironmentPanel.btnEnvTest.setOnClickListener {
            environmentProvider.setCurrentEnvironment(DownloadEnvironment.TEST)
            renderCurrentEnvironment()
        }
        binding.includeEnvironmentPanel.btnEnvProd.setOnClickListener {
            environmentProvider.setCurrentEnvironment(DownloadEnvironment.PROD)
            renderCurrentEnvironment()
        }
    }

    /** 根据当前环境刷新页面展示文案。 */
    private fun renderCurrentEnvironment() {
        val current = environmentProvider.getCurrentEnvironment()
        val config = DownloadEnvironmentEntry(environmentProvider).currentConfig()
        val envBinding = binding.includeEnvironmentPanel
        envBinding.tvCurrentEnvironment.text = getString(R.string.ui_download_environment_current_format, current.name)
        envBinding.tvEnvironmentHint.text = when (current) {
            DownloadEnvironment.DEV -> getString(R.string.ui_download_environment_hint_dev)
            DownloadEnvironment.TEST -> getString(R.string.ui_download_environment_hint_test)
            DownloadEnvironment.PROD -> getString(R.string.ui_download_environment_hint_prod)
        }
        envBinding.tvCatalogSource.text = getString(
            R.string.ui_catalog_source_current_format,
            resolveCatalogSourceText(config),
        )
        envBinding.tvDownloadBaseUrl.text = getString(
            R.string.ui_debug_download_base_url_format,
            config.downloadBaseUrl,
        )
    }

    /** 根据当前环境配置生成目录来源说明。 */
    private fun resolveCatalogSourceText(config: DownloadEnvironmentConfig): String {
        val endpoint = config.catalogEndpointUrl
        return if (endpoint.isNullOrBlank()) {
            getString(R.string.ui_catalog_source_fallback_only)
        } else {
            getString(R.string.ui_catalog_source_http_with_fallback, endpoint)
        }
    }

    /** 绑定策略信号面板，展示当前 Wi‑Fi、存储和驻车状态。 */
    private fun bindPolicySignalsSection() {
        renderPolicySignals()

        binding.includePolicySignalsPanel.btnRefreshSignals.setOnClickListener {
            renderPolicySignals()
        }
    }

    /** 读取当前策略信号并刷新面板文案。 */
    private fun renderPolicySignals() {
        val policyCenter = appServices.policyCenter
        val settings = policyCenter.getSettings()
        val signalsBinding = binding.includePolicySignalsPanel

        signalsBinding.tvWifiStatus.text = getString(
            R.string.ui_debug_wifi_status_format,
            booleanText(settings.wifiConnected),
        )
        signalsBinding.tvStorageStatus.text = getString(
            R.string.ui_debug_storage_status_format,
            booleanText(settings.lowStorageMode),
        )
        signalsBinding.tvParkingStatus.text = getString(
            R.string.ui_debug_parking_status_format,
            booleanText(settings.parkingMode),
        )
    }

    /** 绑定版本信息面板，展示应用版本号和包名。 */
    private fun bindVersionInfoSection() {
        val context = context ?: return
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionBinding = binding.includeVersionInfoPanel
        versionBinding.tvVersionInfo.text = getString(
            R.string.ui_debug_version_format,
            packageInfo.versionName,
            packageInfo.longVersionCode.toInt(),
        )
        versionBinding.tvPackageName.text = getString(
            R.string.ui_debug_package_format,
            context.packageName,
        )
    }

    /** 绑定缓存管理面板，提供清除本地缓存入口。 */
    private fun bindCacheManagementSection() {
        binding.includeCachePanel.btnClearCache.setOnClickListener {
            val context = context ?: return@setOnClickListener
            clearCache(context)
            Toast.makeText(context, getString(R.string.ui_debug_cache_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    /** 清除应用缓存目录。 */
    private fun clearCache(context: Context) {
        val cacheDir = context.cacheDir
        cacheDir?.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
    }

    /** 将布尔值转换为可读文案。 */
    private fun booleanText(value: Boolean): String {
        return if (value) getString(R.string.ui_debug_yes) else getString(R.string.ui_debug_no)
    }

    /** 释放页面 Binding。 */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 创建开发设置页实例。 */
        fun newInstance(): DeveloperSettingsFragment = DeveloperSettingsFragment()
    }
}
