
package com.nio.appstore.feature.installcenter

import com.nio.appstore.common.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nio.appstore.common.base.BaseTaskCenterFragment
import com.nio.appstore.common.ui.TaskCenterText
import com.nio.appstore.common.ui.TaskCenterUiFormatter
import com.nio.appstore.data.model.TaskCenterActionUiState
import com.nio.appstore.data.model.TaskCenterEmptyUiState
import com.nio.appstore.data.model.TaskCenterExtensionUiState
import com.nio.appstore.data.model.TaskCenterFailureUiState
import com.nio.appstore.feature.installcenter.databinding.FragmentInstallCenterBinding
import com.nio.appstore.feature.installcenter.databinding.ViewInstallCenterControlsBinding
import com.nio.appstore.feature.downloadmanager.InstallTaskAdapter
import kotlinx.coroutines.launch

class InstallCenterFragment : BaseTaskCenterFragment() {

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentInstallCenterBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!
    /** 安装中心扩展区控制器。 */
    private var controlsController: InstallCenterControlsController? = null

    /** 安装中心 ViewModel。 */
    private val viewModel: InstallCenterViewModel by viewModels {
        InstallCenterViewModelFactory(
            appServices.appManager,
            appServices.stateCenter,
            appServices.installManager,
            appServices.installSessionStore,
        )
    }

    /** 安装任务列表适配器。 */
    private val adapter by lazy {
        InstallTaskAdapter(
            onPrimaryClick = { item -> viewModel.onPrimaryClick(item.appId, item.primaryAction) },
            onDetailClick = { item -> navigator.openDetail(item.appId) },
        )
    }

    /** 创建安装中心视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInstallCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化安装中心扩展区、列表区和事件绑定。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindCenterTitle(binding.headerBlock, getString(R.string.screen_install_manager_title))
        bindExtensionSlot(
            extensionBinding = binding.extensionSlot,
            uiState = TaskCenterExtensionUiState(
                centerName = getString(R.string.screen_install_center_name),
                title = getString(R.string.screen_install_extension_title),
                hint = getString(R.string.screen_install_extension_hint),
                showPanel = true,
            ),
        )
        // 安装中心把会话控制区挂接到公共扩展插槽中。
        val controlBinding = ViewInstallCenterControlsBinding.inflate(layoutInflater, binding.extensionSlot.extensionContentContainer, false)
        controlsController = InstallCenterControlsController(controlBinding)
        attachExtensionContent(binding.extensionSlot, controlBinding.root)
        controlsController?.bindHandlers(
            com.nio.appstore.common.ui.ThreeActionHandlers(
                onPrimary = viewModel::onBatchStartRunnable,
                onSecondary = viewModel::onClearFailed,
                onTertiary = viewModel::onRetryRetryableSessions,
            ),
        )
        bindEmptyHandlers(
            emptyPanelBinding = binding.emptyPanel,
            onPrimary = navigator::openDownloadManager,
            onSecondary = viewModel::onClearFailed,
        )

        setupListBlock(binding.installTaskBlock, adapter, getString(R.string.screen_install_tasks))
        bindActionHandlers(
            actionBinding = binding.actionBlock,
            onPrimary = viewModel::onCycleFilter,
            onSecondary = viewModel::onCycleSessionFilter,
            onTertiary = viewModel::onBatchStartRunnable,
            onQuaternary = viewModel::onClearFailed,
        )
        observeState()
        viewModel.load()
    }

    /** 订阅安装中心 UI 状态，并刷新会话信息和任务列表。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    controlsController?.bind(state.controlsUiState)

                    // 安装中心头部除了任务统计，还会拼上 Session 维度摘要。
                    bindHeaderBlock(
                        headerBinding = binding.headerBlock,
                        centerName = getString(R.string.screen_install_center_name),
                        subtitle = TaskCenterUiFormatter.subtitle(
                            filter = state.selectedFilter,
                            primaryLabel = getString(R.string.screen_install_primary_label),
                            primaryCount = state.tasks.size,
                            failedCount = state.failedCount,
                        ) + TaskCenterText.sessionFilterLine(
                            state.selectedSessionFilter.label,
                            state.activeSessionCount,
                            state.failedSessionCount,
                            state.recoveredSessionCount,
                        ),
                        hint = listOf(
                            TaskCenterUiFormatter.headerHint(getString(R.string.screen_install_tasks)),
                            TaskCenterUiFormatter.batchSummary(state.selectedFilter, state.tasks.size, state.failedCount),
                            TaskCenterText.sessionSummary(
                                state.activeSessionCount,
                                state.failedSessionCount,
                                state.recoveredSessionCount,
                            ),
                        ).joinToString("\n"),
                        visibleCount = state.tasks.size,
                        totalCount = state.allTaskCount,
                        statsPrefix = getString(R.string.screen_install_primary_label),
                        stats = state.stats,
                    )
                    bindActionBlock(
                        actionBinding = binding.actionBlock,
                        uiState = TaskCenterActionUiState(
                            centerName = getString(R.string.screen_install_center_name),
                            scopeHint = getString(R.string.screen_install_controls_hint),
                            selectedFilter = state.selectedFilter,
                            secondaryText = TaskCenterText.switchSessionView(state.selectedSessionFilter.label),
                            tertiaryText = getString(R.string.screen_install_start_runnable_format, state.batchRunnableCount),
                            quaternaryText = getString(R.string.ui_clear_failed_state_format, state.clearFailedCount),
                            runnableCount = state.batchRunnableCount,
                            failedCount = state.clearFailedCount,
                        ),
                    )
                    // 失败面板、空态面板和列表区都跟随当前任务和 Session 过滤结果更新。
                    bindEmptyPanel(
                        emptyPanelBinding = binding.emptyPanel,
                        uiState = TaskCenterEmptyUiState(
                            centerName = getString(R.string.screen_install_center_name),
                            selectedFilter = state.selectedFilter,
                            primaryText = getString(R.string.ui_go_download_manager),
                            secondaryText = getString(R.string.ui_clear_failed_state),
                            showEmpty = state.tasks.isEmpty(),
                            showSecondary = state.showFailurePanel,
                        ),
                    )
                    bindListBlock(
                        listBlockBinding = binding.installTaskBlock,
                        sectionName = getString(R.string.screen_install_tasks),
                        visible = state.tasks.isNotEmpty(),
                    )
                    bindFailurePanel(
                        failureBinding = binding.failurePanel,
                        uiState = TaskCenterFailureUiState(
                            centerName = getString(R.string.screen_install_center_name),
                            failedCount = state.clearFailedCount,
                            primaryText = getString(R.string.screen_install_retry_failed_format, state.failedCount),
                            secondaryText = getString(R.string.ui_clear_failed_state_format, state.clearFailedCount),
                            showPanel = state.showFailurePanel,
                        ),
                    )
                    bindFailureHandlers(
                        failureBinding = binding.failurePanel,
                        onPrimary = viewModel::onRetryFailed,
                        onSecondary = viewModel::onClearFailed,
                    )

                    adapter.submitList(state.tasks)
                }
            }
        }
    }

    /** 释放安装中心页面相关引用。 */
    override fun onDestroyView() {
        super.onDestroyView()
        controlsController = null
        _binding = null
    }

    companion object {
        /** 创建安装中心实例。 */
        fun newInstance() = InstallCenterFragment()
    }
}
