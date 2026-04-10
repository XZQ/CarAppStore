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

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentMyAppBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!

    /** 我的应用页 ViewModel。 */
    private val viewModel: MyAppViewModel by viewModels {
        MyAppViewModelFactory(appServices.appManager, appServices.stateCenter)
    }

    /** 我的应用列表适配器。 */
    private val adapter by lazy {
        MyAppAdapter { app -> navigator.openDetail(app.appId) }
    }

    /** 创建我的应用页视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化列表、标题和状态订阅。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_my_apps_title))
        binding.recyclerMyApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMyApps.adapter = adapter
        observeState()
        viewModel.load()
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
    }

    /** 订阅我的应用页 UI 状态，并刷新列表。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 根据结果数量切换副标题文案。
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

    /** 释放我的应用页 Binding。 */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 创建我的应用页实例。 */
        fun newInstance() = MyAppFragment()
    }
}
