package com.nio.appstore.feature.debug

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nio.appstore.common.R
import com.nio.appstore.data.downloadenv.DownloadEnvironment
import com.nio.appstore.data.downloadenv.LocalDownloadEnvironmentProvider
import com.nio.appstore.common.navigation.MainNavigator
import com.nio.appstore.feature.debug.databinding.FragmentDeveloperSettingsBinding

class DeveloperSettingsFragment : Fragment() {

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentDeveloperSettingsBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!

    /** 顶部导航控制器。 */
    private lateinit var navigator: MainNavigator
    /** 下载环境配置读写入口。 */
    private lateinit var environmentProvider: LocalDownloadEnvironmentProvider

    /** 在附着到 Activity 时初始化导航器和环境配置入口。 */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        navigator = context as MainNavigator
        environmentProvider = LocalDownloadEnvironmentProvider(context.applicationContext)
    }

    /** 创建开发设置页视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeveloperSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化页面标题和环境切换区。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.ui_developer_settings))
        bindEnvironmentSection()
    }

    /** 绑定下载环境切换按钮。 */
    private fun bindEnvironmentSection() {
        renderCurrentEnvironment()

        binding.btnEnvDev.setOnClickListener {
            environmentProvider.setCurrentEnvironment(DownloadEnvironment.DEV)
            renderCurrentEnvironment()
        }
        binding.btnEnvTest.setOnClickListener {
            environmentProvider.setCurrentEnvironment(DownloadEnvironment.TEST)
            renderCurrentEnvironment()
        }
        binding.btnEnvProd.setOnClickListener {
            environmentProvider.setCurrentEnvironment(DownloadEnvironment.PROD)
            renderCurrentEnvironment()
        }
    }

    /** 根据当前环境刷新页面展示文案。 */
    private fun renderCurrentEnvironment() {
        val current = environmentProvider.getCurrentEnvironment()
        binding.tvCurrentEnvironment.text = getString(R.string.ui_download_environment_current_format, current.name)
        binding.tvEnvironmentHint.text = when (current) {
            DownloadEnvironment.DEV -> getString(R.string.ui_download_environment_hint_dev)
            DownloadEnvironment.TEST -> getString(R.string.ui_download_environment_hint_test)
            DownloadEnvironment.PROD -> getString(R.string.ui_download_environment_hint_prod)
        }
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
