
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

    private var _binding: FragmentUpgradeBinding? = null
    private val binding get() = _binding!!
    private var controlsController: UpgradeCenterControlsController? = null

    private val viewModel: UpgradeViewModel by viewModels {
        UpgradeViewModelFactory(appServices.appManager, appServices.stateCenter, appServices.upgradeManager)
    }

    private val adapter by lazy {
        UpgradeTaskAdapter(
            onPrimaryClick = { task -> viewModel.onPrimaryClick(task) },
            onDetailClick = { task -> navigator.openDetail(task.appId) },
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentUpgradeBinding.inflate(inflater, container, false)
        return binding.root
    }

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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    controlsController?.bind(state.controlsUiState)

                    bindHeaderBlock(
                        headerBinding = binding.headerBlock,
                        centerName = getString(R.string.screen_upgrade_center_name),
                        subtitle = TaskCenterUiFormatter.subtitle(
                            filter = state.selectedFilter,
                            primaryLabel = getString(R.string.screen_upgrade_primary_label),
                            primaryCount = state.tasks.size,
                            failedCount = state.failedCount,
                        ),
                        hint = TaskCenterUiFormatter.headerHint(getString(R.string.screen_upgrade_tasks)) + "\n" +
                            TaskCenterUiFormatter.batchSummary(state.selectedFilter, state.tasks.size, state.failedCount),
                        visibleCount = state.tasks.size,
                        totalCount = state.availableCount,
                        statsPrefix = getString(R.string.screen_upgrade_primary_label),
                        stats = state.stats,
                    )
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
                    bindFailurePanel(
                        failureBinding = binding.failurePanel,
                        uiState = TaskCenterFailureUiState(
                            centerName = getString(R.string.screen_upgrade_center_name),
                            failedCount = state.failedCount,
                            primaryText = getString(R.string.screen_upgrade_retry_failed_format, state.failedCount),
                            secondaryText = getString(R.string.screen_upgrade_start_runnable_format, state.batchRunnableCount),
                            showPanel = state.failedCount > 0,
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
                            showEmpty = state.tasks.isEmpty(),
                            showSecondary = true,
                        ),
                    )
                    bindListBlock(
                        listBlockBinding = binding.upgradeTaskBlock,
                        sectionName = getString(R.string.screen_upgrade_tasks),
                        visible = state.tasks.isNotEmpty(),
                    )
                    adapter.submitList(state.tasks)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controlsController = null
        _binding = null
    }

    companion object {
        fun newInstance() = UpgradeFragment()
    }
}
