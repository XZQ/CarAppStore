package com.xzq.appstore.feature.myapp

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
import com.xzq.appstore.feature.myapp.databinding.FragmentMyAppBinding
import kotlinx.coroutines.launch

class MyAppFragment : BaseFragment() {
    private var _binding: FragmentMyAppBinding? = null

    private val binding get() = requireNotNull(_binding) { "Binding 已销毁" }

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
        binding.recyclerMyApps.layoutManager = GridLayoutManager(requireContext(), resolveSpanCount())
        binding.recyclerMyApps.adapter = adapter
        observeState()
        viewModel.load()
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
        binding.btnInstallManager.setOnClickListener { navigator.openInstallManager() }
        binding.btnDownloadManager.setOnClickListener { navigator.openDownloadManager() }
        binding.btnUpgradeManager.setOnClickListener { navigator.openUpgradeManager() }
    }

    /** 根据当前屏幕宽度切换列表列数。 */
    private fun resolveSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return when {
            widthDp >= 1080 -> 3
            widthDp >= 600 -> 2
            else -> 1
        }
    }

    /** 订阅我的应用页 UI 状态，并刷新列表。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 根据结果数量切换副标题文案。
                    binding.tvMyAppSubtitle.text = when (val screenState = state.screenState) {
                        MyAppScreenState.Loading -> getString(R.string.loading)
                        MyAppScreenState.Content -> getString(R.string.screen_my_apps_count, state.apps.size)
                        MyAppScreenState.Empty -> getString(R.string.screen_my_apps_empty_tasks)
                        is MyAppScreenState.Error -> screenState.message.ifBlank {
                            getString(R.string.screen_my_apps_error_hint)
                        }
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
        /** 创建我的应用页实例。 */
        fun newInstance() = MyAppFragment()
    }
}
