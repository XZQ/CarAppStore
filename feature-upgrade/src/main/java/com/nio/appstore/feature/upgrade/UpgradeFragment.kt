
package com.nio.appstore.feature.upgrade

import com.nio.appstore.common.R
import com.nio.appstore.data.model.TaskCenterActionUiState
import com.nio.appstore.data.model.TaskCenterEmptyUiState
import com.nio.appstore.data.model.TaskCenterFailureUiState
import com.nio.appstore.data.model.TaskCenterExtensionUiState
import com.nio.appstore.feature.upgrade.databinding.ViewUpgradeCenterControlsBinding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nio.appstore.common.base.BaseTaskCenterFragment
import com.nio.appstore.common.ui.TaskCenterUiFormatter
import com.nio.appstore.feature.upgrade.databinding.FragmentUpgradeBinding
import kotlinx.coroutines.launch

class UpgradeFragment : BaseTaskCenterFragment() {

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentUpgradeBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!
    /** 升级中心扩展区控制器。 */
    private var controlsController: UpgradeCenterControlsController? = null

    /** 升级中心 ViewModel。 */
    private val viewModel: UpgradeViewModel by viewModels {
        UpgradeViewModelFactory(appServices.appManager, appServices.stateCenter, appServices.upgradeManager)
    }

    /** 升级任务列表适配器。 */
    private val adapter by lazy {
        UpgradeTaskAdapter(
            onPrimaryClick = { task -> viewModel.onPrimaryClick(task) },
            onDetailClick = { task -> navigator.openDetail(task.appId) },
        )
    }

    /** 创建升级中心视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUpgradeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化升级中心扩展区、列表区和事件绑定。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindCenterTitle(binding.headerBlock, getString(R.string.screen_upgrade_manager_title))
        bindExtensionSlot(
            extensionBinding = binding.extensionSlot,
            uiState = TaskCenterExtensionUiState(
                centerName = getString(R.string.screen_upgrade_center_name),
                title = getString(R.string.screen_upgrade_extension_title),
                hint = getString(R.string.screen_upgrade_extension_hint),
                showPanel = true,
            ),
        )
        // 升级中心把批量升级控制区挂接到公共扩展插槽中。
        val controlBinding = ViewUpgradeCenterControlsBinding.inflate(layoutInflater, binding.extensionSlot.extensionContentContainer, false)
        controlsController = UpgradeCenterControlsController(controlBinding)
        attachExtensionContent(binding.extensionSlot, controlBinding.root)
        controlsController?.bindHandlers(
            com.nio.appstore.common.ui.ThreeActionHandlers(
                onPrimary = viewModel::onStartAllRunnable,
                onSecondary = viewModel::onRetryFailed,
                onTertiary = navigator::openMyApps,
            ),
        )
        bindEmptyHandlers(
            emptyPanelBinding = binding.emptyPanel,
            onPrimary = navigator::openHome,
            onSecondary = navigator::openMyApps,
        )

        setupListBlock(binding.upgradeTaskBlock, adapter, getString(R.string.screen_upgrade_tasks))
        bindActionHandlers(
            actionBinding = binding.actionBlock,
            onPrimary = viewModel::onCycleFilter,
            onSecondary = viewModel::onRetryFailed,
            onTertiary = viewModel::onStartAllRunnable,
        )
        observeState()
        viewModel.load()
    }

    /** 订阅升级中心 UI 状态，并刷新头部、扩展区和任务列表。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val showTaskPanels = state.screenState == UpgradeScreenState.Content
                    val showChrome = state.screenState !is UpgradeScreenState.Error
                    val subtitle = when (val screenState = state.screenState) {
                        UpgradeScreenState.Loading -> getString(R.string.loading)
                        UpgradeScreenState.Content -> TaskCenterUiFormatter.subtitle(
                            filter = state.selectedFilter,
                            primaryLabel = getString(R.string.screen_upgrade_primary_label),
                            primaryCount = state.tasks.size,
                            failedCount = state.failedCount,
                        )
                        UpgradeScreenState.Empty -> getString(R.string.screen_upgrade_empty_hint)
                        is UpgradeScreenState.Error -> screenState.message.ifBlank {
                            getString(R.string.screen_upgrade_error_hint)
                        }
                    }
                    val hint = when (val screenState = state.screenState) {
                        UpgradeScreenState.Loading -> getString(R.string.screen_upgrade_loading_hint)
                        UpgradeScreenState.Content,
                        UpgradeScreenState.Empty,
                        -> listOf(
                            TaskCenterUiFormatter.headerHint(getString(R.string.screen_upgrade_tasks)),
                            TaskCenterUiFormatter.batchSummary(state.selectedFilter, state.tasks.size, state.failedCount),
                        ).joinToString("\n")
                        is UpgradeScreenState.Error -> screenState.message.ifBlank {
                            getString(R.string.screen_upgrade_error_hint)
                        }
                    }
                    controlsController?.bind(state.controlsUiState)

                    // 头部区优先展示当前筛选下的升级统计和批量处理提示。
                    bindHeaderBlock(
                        headerBinding = binding.headerBlock,
                        centerName = getString(R.string.screen_upgrade_center_name),
                        subtitle = subtitle,
                        hint = hint,
                        visibleCount = state.tasks.size,
                        totalCount = state.availableCount,
                        statsPrefix = getString(R.string.screen_upgrade_primary_label),
                        stats = state.stats,
                    )
                    binding.actionBlock.root.visibility = if (showChrome) View.VISIBLE else View.GONE
                    binding.extensionSlot.root.visibility = if (showChrome) View.VISIBLE else View.GONE
                    bindActionBlock(
                        actionBinding = binding.actionBlock,
                        uiState = TaskCenterActionUiState(
                            centerName = getString(R.string.screen_upgrade_center_name),
                            scopeHint = getString(R.string.screen_upgrade_controls_hint),
                            selectedFilter = state.selectedFilter,
                            secondaryText = getString(R.string.screen_upgrade_retry_failed_format, state.failedCount),
                            tertiaryText = getString(R.string.screen_upgrade_start_runnable_format, state.batchRunnableCount),
                            quaternaryText = null,
                            runnableCount = state.batchRunnableCount,
                            failedCount = state.failedCount,
                        ),
                    )
                    // 失败面板和空态面板根据当前筛选和失败数动态切换。
                    bindFailurePanel(
                        failureBinding = binding.failurePanel,
                        uiState = TaskCenterFailureUiState(
                            centerName = getString(R.string.screen_upgrade_center_name),
                            failedCount = state.failedCount,
                            primaryText = getString(R.string.screen_upgrade_retry_failed_format, state.failedCount),
                            secondaryText = getString(R.string.screen_upgrade_start_runnable_format, state.batchRunnableCount),
                            showPanel = showChrome && state.showFailurePanel,
                            showSecondary = state.batchRunnableCount > 0,
                        ),
                    )
                    bindFailureHandlers(
                        failureBinding = binding.failurePanel,
                        onPrimary = viewModel::onRetryFailed,
                        onSecondary = viewModel::onStartAllRunnable,
                    )

                    bindEmptyPanel(
                        emptyPanelBinding = binding.emptyPanel,
                        uiState = TaskCenterEmptyUiState(
                            centerName = getString(R.string.screen_upgrade_center_name),
                            selectedFilter = state.selectedFilter,
                            primaryText = getString(R.string.screen_download_go_home),
                            secondaryText = getString(R.string.screen_download_go_my_apps),
                            showEmpty = state.screenState == UpgradeScreenState.Empty || state.screenState is UpgradeScreenState.Error,
                            showSecondary = true,
                        ),
                    )
                    bindListBlock(
                        listBlockBinding = binding.upgradeTaskBlock,
                        sectionName = getString(R.string.screen_upgrade_tasks),
                        visible = showTaskPanels && state.tasks.isNotEmpty(),
                    )
                    adapter.submitList(state.tasks)
                }
            }
        }
    }

    /** 释放升级中心页面相关引用。 */
    override fun onDestroyView() {
        super.onDestroyView()
        controlsController = null
        _binding = null
    }

    companion object {
        /** 创建升级中心实例。 */
        fun newInstance() = UpgradeFragment()
    }
}
