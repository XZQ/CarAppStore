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

    private var _binding: FragmentDeveloperSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var navigator: MainNavigator
    private lateinit var environmentProvider: LocalDownloadEnvironmentProvider

    override fun onAttach(context: Context) {
        super.onAttach(context)
        navigator = context as MainNavigator
        environmentProvider = LocalDownloadEnvironmentProvider(context.applicationContext)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeveloperSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.ui_developer_settings))
        bindEnvironmentSection()
    }

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

    private fun renderCurrentEnvironment() {
        val current = environmentProvider.getCurrentEnvironment()
        binding.tvCurrentEnvironment.text = getString(R.string.ui_download_environment_current_format, current.name)
        binding.tvEnvironmentHint.text = when (current) {
            DownloadEnvironment.DEV -> getString(R.string.ui_download_environment_hint_dev)
            DownloadEnvironment.TEST -> getString(R.string.ui_download_environment_hint_test)
            DownloadEnvironment.PROD -> getString(R.string.ui_download_environment_hint_prod)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): DeveloperSettingsFragment = DeveloperSettingsFragment()
    }
}
