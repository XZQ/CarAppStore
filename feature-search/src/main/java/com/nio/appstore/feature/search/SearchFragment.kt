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

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(appServices.appManager, appServices.stateCenter)
    }

    private val adapter by lazy { HomeAdapter { app -> navigator.openDetail(app.appId) } }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_search_title))
        binding.recyclerSearch.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSearch.adapter = adapter
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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = SearchFragment()
    }
}
