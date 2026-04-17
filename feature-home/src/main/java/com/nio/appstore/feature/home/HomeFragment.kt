package com.nio.appstore.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nio.appstore.common.R
import com.nio.appstore.common.base.BaseFragment
import com.nio.appstore.feature.home.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment() {

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentHomeBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!

    /** 首页 ViewModel。 */
    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            appServices.appManager,
            appServices.stateCenter,
            appServices.downloadManager,
            appServices.installManager,
            appServices.upgradeManager,
            appServices.policyCenter,
        )
    }

    /** 首页应用列表适配器。 */
    private val homeAdapter by lazy {
        HomeAdapter(
            onPrimaryClick = { app -> viewModel.onPrimaryClick(app) },
            onDetailClick = { app -> navigator.openDetail(app.appId) },
        )
    }

    /** 创建首页视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化首页列表、标题和状态订阅。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_home_title))
        binding.recyclerHome.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHome.adapter = homeAdapter
        observeState()
        viewModel.load()
        binding.btnMyApps.setOnClickListener { navigator.openMyApps() }
    }

    /** 订阅首页 UI 状态，并刷新列表与顶部提示。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 根据页面状态切换副标题，同时控制策略提示的显示与隐藏。
                    binding.tvHomeSubtitle.text = when (val screenState = state.screenState) {
                        HomeScreenState.Loading -> getString(R.string.loading)
                        HomeScreenState.Content -> getString(R.string.screen_home_recommend_count, state.apps.size)
                        HomeScreenState.Empty -> getString(R.string.screen_home_empty_apps)
                        is HomeScreenState.Error -> screenState.message.ifBlank {
                            getString(R.string.screen_home_error_hint)
                        }
                    }
                    binding.tvPolicyPrompt.text = state.policyPrompt
                    binding.tvPolicyPrompt.visibility = if (state.policyPrompt.isBlank()) View.GONE else View.VISIBLE
                    homeAdapter.submitList(state.apps)
                }
            }
        }
    }

    /** 释放首页 Binding。 */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 创建首页实例。 */
        fun newInstance() = HomeFragment()
    }
}
