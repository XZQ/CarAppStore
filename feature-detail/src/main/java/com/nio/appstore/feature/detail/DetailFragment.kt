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

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentDetailBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!

    /** 当前详情页对应的应用标识。 */
    private val appId: String by lazy {
        requireArguments().getString(ARG_APP_ID).orEmpty()
    }

    /** 详情页 ViewModel。 */
    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory(
            appManager = appServices.appManager,
            downloadManager = appServices.downloadManager,
            installManager = appServices.installManager,
            upgradeManager = appServices.upgradeManager,
            stateCenter = appServices.stateCenter,
        )
    }

    /** 创建详情页视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化详情页标题、状态订阅和按钮事件。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_detail_title))
        observeState()
        viewModel.load(appId)

        binding.btnPrimaryAction.setOnClickListener { viewModel.onPrimaryClick() }
        binding.btnGoMyApps.setOnClickListener { navigator.openMyApps() }
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
    }

    /** 订阅详情页 UI 状态，并刷新详情信息与主动作。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvDetailName.text = state.appDetail?.name ?: ""
                    binding.tvDetailVersion.text = getString(R.string.screen_detail_version_format, state.appDetail?.versionName.orEmpty())
                    binding.tvDetailDesc.text = state.appDetail?.description ?: ""
                    binding.tvState.applyTagStyle(CarUiStyle.tagStyle(state.stateText, state.statusTone))
                    // 进度和主动作始终跟随状态中心与业务编排结果刷新。
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

    /** 释放详情页 Binding。 */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Bundle 中应用标识的键名。 */
        private const val ARG_APP_ID = "arg_app_id"

        /** 创建指定应用的详情页实例。 */
        fun newInstance(appId: String) = DetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_APP_ID, appId)
            }
        }
    }
}
