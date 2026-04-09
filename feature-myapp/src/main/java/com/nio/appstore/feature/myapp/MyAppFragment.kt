package com.nio.appstore.feature.myapp

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
import com.nio.appstore.feature.myapp.databinding.FragmentMyAppBinding
import kotlinx.coroutines.launch

class MyAppFragment : BaseFragment() {

    private var _binding: FragmentMyAppBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyAppViewModel by viewModels {
        MyAppViewModelFactory(appServices.appManager, appServices.stateCenter)
    }

    private val adapter by lazy {
        MyAppAdapter { app -> navigator.openDetail(app.appId) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMyAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_my_apps_title))
        binding.recyclerMyApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMyApps.adapter = adapter
        observeState()
        viewModel.load()
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvMyAppSubtitle.text = if (state.apps.isEmpty()) {
                        getString(R.string.screen_my_apps_empty_tasks)
                    } else {
                        getString(R.string.screen_my_apps_count, state.apps.size)
                    }
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
        fun newInstance() = MyAppFragment()
    }
}
