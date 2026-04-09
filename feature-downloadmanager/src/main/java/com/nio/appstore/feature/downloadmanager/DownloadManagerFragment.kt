
package com.nio.appstore.feature.downloadmanager

import com.nio.appstore.common.R
import com.nio.appstore.data.model.TaskCenterActionUiState
import com.nio.appstore.data.model.TaskCenterEmptyUiState
import com.nio.appstore.data.model.TaskCenterFailureUiState
import com.nio.appstore.data.model.TaskCenterExtensionUiState

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
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.feature.downloadmanager.databinding.FragmentDownloadManagerBinding
import com.nio.appstore.feature.downloadmanager.databinding.ViewDownloadCenterPreferencesBinding
import kotlinx.coroutines.launch

class DownloadManagerFragment : BaseTaskCenterFragment() {

    private var _binding: FragmentDownloadManagerBinding? = null
    private val binding get() = _binding!!
    private var preferencesBinding: ViewDownloadCenterPreferencesBinding? = null
    private var preferencesController: DownloadCenterPreferencesController? = null

    private val viewModel: DownloadManagerViewModel by viewModels {
        DownloadManagerViewModelFactory(
            appServices.appManager,
            appServices.stateCenter,
            appServices.downloadManager,
            appServices.installManager,
            appServices.policyCenter,
        )
    }

    private val downloadAdapter by lazy {
        DownloadTaskAdapter(
            onPrimaryClick = { item -> viewModel.onPrimaryClick(item) },
            onSecondaryClick = { item -> viewModel.onSecondaryClick(item) },
            onDetailClick = { item -> navigator.openDetail(item.appId) },
        )
    }

    private val installAdapter by lazy {
        InstallTaskAdapter(
            onPrimaryClick = { item -> viewModel.onInstallPrimaryClick(item) },
            onDetailClick = { item -> navigator.openDetail(item.appId) },
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindCenterTitle(binding.headerBlock, getString(R.string.screen_download_manager_title))
        bindExtensionSlot(
            extensionBinding = binding.extensionSlot,
            uiState = TaskCenterExtensionUiState(
                centerName = getString(R.string.screen_download_center_name),
                title = getString(R.string.screen_download_extension_title),
                hint = getString(R.string.screen_download_extension_hint),
                showPanel = true,
            ),
        )
        val prefsBinding = ViewDownloadCenterPreferencesBinding.inflate(layoutInflater, binding.extensionSlot.extensionContentContainer, false)
        preferencesBinding = prefsBinding
        preferencesController = DownloadCenterPreferencesController(prefsBinding)
        attachExtensionContent(binding.extensionSlot, prefsBinding.root)

        bindEmptyHandlers(
            emptyPanelBinding = binding.emptyPanel,
            onPrimary = navigator::openHome,
            onSecondary = navigator::openMyApps,
        )

        setupListBlock(binding.installTaskBlock, installAdapter, getString(R.string.screen_install_tasks))
        setupListBlock(binding.downloadTaskBlock, downloadAdapter, getString(R.string.screen_download_tasks))

        bindActionHandlers(
            actionBinding = binding.actionBlock,
            onPrimary = viewModel::onCycleFilter,
            onSecondary = viewModel::onRetryFailed,
            onTertiary = viewModel::onBatchInstallReady,
            onQuaternary = viewModel::onClearCompleted,
        )
        preferencesController?.bindHandlers(
            com.nio.appstore.common.ui.FiveActionHandlers(
                onFirst = viewModel::onToggleAutoResume,
                onSecond = viewModel::onToggleAutoRetry,
                onThird = viewModel::onToggleWifi,
                onFourth = viewModel::onToggleParking,
                onFifth = viewModel::onToggleStorage,
            ),
        )
        observeState()
        viewModel.load()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val visibleCount = state.tasks.size + state.installTasks.size
                    val readyInstallCount = state.tasks.count { it.primaryAction.name.contains("INSTALL") }
                    val totalStats = TaskCenterStats(
                        activeCount = state.downloadStats.activeCount + state.installStats.activeCount,
                        pendingCount = state.downloadStats.pendingCount + state.installStats.pendingCount,
                        failedCount = state.downloadStats.failedCount + state.installStats.failedCount,
                        completedCount = state.downloadStats.completedCount + state.installStats.completedCount,
                    )

                    bindHeaderBlock(
                        headerBinding = binding.headerBlock,
                        centerName = getString(R.string.screen_download_center_name),
                        subtitle = TaskCenterUiFormatter.subtitle(
                            filter = state.selectedFilter,
                            primaryLabel = getString(R.string.screen_download_primary_label),
                            primaryCount = state.tasks.size,
                            secondaryLabel = getString(R.string.screen_download_secondary_label),
                            secondaryCount = state.installTasks.size,
                            failedCount = state.failedCount,
                        ),
                        hint = TaskCenterUiFormatter.headerHint(getString(R.string.screen_download_overview_title)) + "\n" +
                            TaskCenterUiFormatter.batchSummary(state.selectedFilter, visibleCount, state.failedCount),
                        visibleCount = visibleCount,
                        totalCount = state.allTaskCount,
                        statsPrefix = getString(R.string.screen_download_overview_hint),
                        stats = totalStats,
                    )
                    bindActionBlock(
                        actionBinding = binding.actionBlock,
                        uiState = TaskCenterActionUiState(
                            centerName = getString(R.string.screen_download_center_name),
                            scopeHint = getString(R.string.screen_download_controls_hint),
                            selectedFilter = state.selectedFilter,
                            secondaryText = getString(R.string.screen_download_retry_failed_format, state.failedCount),
                            tertiaryText = getString(R.string.screen_download_install_ready_format, readyInstallCount),
                            quaternaryText = getString(R.string.screen_download_clear_completed),
                            runnableCount = readyInstallCount,
                            failedCount = state.failedCount,
                        ),
                    )
                    bindFailurePanel(
                        failureBinding = binding.failurePanel,
                        uiState = TaskCenterFailureUiState(
                            centerName = getString(R.string.screen_download_center_name),
                            failedCount = state.failedCount,
                            primaryText = getString(R.string.screen_download_retry_failed_format, state.failedCount),
                            secondaryText = getString(R.string.action_clear_failed),
                            showPanel = state.failedCount > 0,
                        ),
                    )
                    bindFailureHandlers(
                        failureBinding = binding.failurePanel,
                        onPrimary = viewModel::onRetryFailed,
                        onSecondary = viewModel::onClearFailed,
                    )

                    bindEmptyPanel(
                        emptyPanelBinding = binding.emptyPanel,
                        uiState = TaskCenterEmptyUiState(
                            centerName = getString(R.string.screen_download_center_name),
                            selectedFilter = state.selectedFilter,
                            primaryText = getString(R.string.screen_download_go_home),
                            secondaryText = getString(R.string.screen_download_go_my_apps),
                            showEmpty = visibleCount == 0,
                            showSecondary = true,
                        ),
                    )
                    bindListBlock(
                        listBlockBinding = binding.installTaskBlock,
                        sectionName = getString(R.string.screen_install_tasks),
                        visible = state.installTasks.isNotEmpty() && visibleCount != 0,
                    )
                    bindListBlock(
                        listBlockBinding = binding.downloadTaskBlock,
                        sectionName = getString(R.string.screen_download_tasks),
                        visible = visibleCount != 0,
                    )

                    preferencesController?.bind(state.preferencesUiState)

                    installAdapter.submitList(state.installTasks)
                    downloadAdapter.submitList(state.tasks)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preferencesController = null
        preferencesBinding = null
        _binding = null
    }

    companion object {
        fun newInstance() = DownloadManagerFragment()
    }
}
