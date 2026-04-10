package com.nio.appstore.feature.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nio.appstore.common.R
import com.nio.appstore.common.base.BaseFragment
import com.nio.appstore.feature.search.databinding.FragmentSearchBinding
import com.nio.appstore.feature.home.HomeAdapter
import kotlinx.coroutines.launch

class SearchFragment : BaseFragment() {

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentSearchBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!

    /** 搜索页 ViewModel。 */
    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(
            appServices.appManager,
            appServices.stateCenter,
            appServices.downloadManager,
            appServices.installManager,
            appServices.upgradeManager,
        )
    }

    /** 搜索结果列表适配器。 */
    private val adapter by lazy {
        HomeAdapter(
            onPrimaryClick = { app -> viewModel.onPrimaryClick(app) },
            onDetailClick = { app -> navigator.openDetail(app.appId) },
        )
    }

    /** 创建搜索页视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化搜索框、列表和状态订阅。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_search_title))
        binding.recyclerSearch.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSearch.adapter = adapter
        // 输入变化后立即驱动搜索状态更新。
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.search(text?.toString().orEmpty())
        }
        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            viewModel.search("")
        }
        observeState()
        viewModel.load()
    }

    /** 订阅搜索页 UI 状态，并同步输入框与结果列表。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 状态恢复时，需要把 ViewModel 中的关键词同步回输入框。
                    if (binding.etSearch.text?.toString() != state.keyword) {
                        binding.etSearch.setText(state.keyword)
                        binding.etSearch.setSelection(state.keyword.length)
                    }
                    binding.tvSearchSubtitle.text = if (state.apps.isEmpty()) {
                        getString(R.string.screen_search_empty_result)
                    } else {
                        getString(R.string.screen_search_result_count, state.apps.size)
                    }
                    binding.tvPolicyPrompt.text = state.policyPrompt
                    binding.tvPolicyPrompt.visibility = if (state.policyPrompt.isBlank()) View.GONE else View.VISIBLE
                    adapter.submitList(state.apps)
                }
            }
        }
    }

    /** 释放搜索页 Binding。 */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 创建搜索页实例。 */
        fun newInstance() = SearchFragment()
    }
}
