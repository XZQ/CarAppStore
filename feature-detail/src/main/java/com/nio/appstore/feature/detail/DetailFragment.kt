package com.nio.appstore.feature.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nio.appstore.common.R
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nio.appstore.common.base.BaseFragment
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.common.ui.applyActionStyle
import com.nio.appstore.common.ui.applyTagStyle
import com.nio.appstore.feature.detail.databinding.FragmentDetailBinding
import kotlinx.coroutines.launch

class DetailFragment : BaseFragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val appId: String by lazy {
        requireArguments().getString(ARG_APP_ID).orEmpty()
    }

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory(
            appManager = appServices.appManager,
            downloadManager = appServices.downloadManager,
            installManager = appServices.installManager,
            upgradeManager = appServices.upgradeManager,
            stateCenter = appServices.stateCenter,
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_detail_title))
        observeState()
        viewModel.load(appId)

        binding.btnPrimaryAction.setOnClickListener { viewModel.onPrimaryClick() }
        binding.btnGoMyApps.setOnClickListener { navigator.openMyApps() }
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvDetailName.text = state.appDetail?.name ?: ""
                    binding.tvDetailVersion.text = getString(R.string.screen_detail_version_format, state.appDetail?.versionName.orEmpty())
                    binding.tvDetailDesc.text = state.appDetail?.description ?: ""
                    binding.tvState.applyTagStyle(CarUiStyle.tagStyle(state.stateText, state.statusTone))
                    binding.progressDownload.progress = state.progress
                    binding.tvProgress.text = if (state.progress > 0) {
                        getString(R.string.screen_detail_progress_format, state.progress)
                    } else {
                        getString(R.string.screen_detail_no_progress)
                    }
                    binding.btnPrimaryAction.applyActionStyle(CarUiStyle.actionStyle(state.primaryAction))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_APP_ID = "arg_app_id"

        fun newInstance(appId: String) = DetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_APP_ID, appId)
            }
        }
    }
}
