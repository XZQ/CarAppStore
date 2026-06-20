package com.xzq.appstore.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.xzq.appstore.common.R
import com.xzq.appstore.common.base.BaseFragment
import com.xzq.appstore.feature.home.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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

    private val homeAdapter by lazy {
        HomeAdapter(
            onPrimaryClick = { app -> viewModel.onPrimaryClick(app) },
            onDetailClick = { app -> navigator.openDetail(app.appId) },
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_home_title))
        binding.recyclerHome.layoutManager = GridLayoutManager(requireContext(), resolveSpanCount())
        binding.recyclerHome.adapter = homeAdapter
        binding.btnSearchApps.setOnClickListener { navigator.openSearch() }
        binding.btnMyApps.setOnClickListener { navigator.openMyApps() }
        observeState()
        viewModel.load()
    }

    private fun resolveSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return when {
            widthDp >= 1080 -> 3
            widthDp >= 600 -> 2
            else -> 1
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
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
                    binding.tvRecentApps.text = if (state.recentApps.isEmpty()) {
                        getString(R.string.screen_home_recent_empty)
                    } else {
                        state.recentApps.joinToString("  |  ") { it.name }
                    }
                    homeAdapter.submitList(state.apps)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
