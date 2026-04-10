
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
import com.nio.appstore.feature.downloadmanager.databinding.FragmentDownloadManagerBinding
import com.nio.appstore.feature.downloadmanager.databinding.ViewDownloadCenterPreferencesBinding
import kotlinx.coroutines.launch

class DownloadManagerFragment : BaseTaskCenterFragment() {

    /** 当前页面的 ViewBinding。 */
    private var _binding: FragmentDownloadManagerBinding? = null
    /** 对外暴露的非空 Binding 访问入口。 */
    private val binding get() = _binding!!
    /** 下载偏好扩展区的 ViewBinding。 */
    private var preferencesBinding: ViewDownloadCenterPreferencesBinding? = null
    /** 下载偏好扩展区控制器。 */
    private var preferencesController: DownloadCenterPreferencesController? = null

    /** 下载中心 ViewModel。 */
    private val viewModel: DownloadManagerViewModel by viewModels {
        DownloadManagerViewModelFactory(
            appServices.appManager,
            appServices.stateCenter,
            appServices.downloadManager,
            appServices.installManager,
            appServices.policyCenter,
        )
    }

    /** 下载任务列表适配器。 */
    private val downloadAdapter by lazy {
        DownloadTaskAdapter(
            onPrimaryClick = { item -> viewModel.onPrimaryClick(item) },
            onSecondaryClick = { item -> viewModel.onSecondaryClick(item) },
            onDetailClick = { item -> navigator.openDetail(item.appId) },
        )
    }

    /** 待安装任务列表适配器。 */
    private val installAdapter by lazy {
        InstallTaskAdapter(
            onPrimaryClick = { item -> viewModel.onInstallPrimaryClick(item) },
            onDetailClick = { item -> navigator.openDetail(item.appId) },
        )
    }

    /** 创建下载中心视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化下载中心的扩展区、列表区和事件绑定。 */
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
        // 下载中心把偏好设置扩展区挂接到公共扩展插槽中。
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

        // 批量动作区负责筛选切换、失败重试、安装就绪和已完成清理。
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

    /** 订阅下载中心 UI 状态，并刷新头部、扩展区和任务列表。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 头部摘要同时展示下载任务和待安装任务的联合统计。
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
                        hint = listOf(
                            TaskCenterUiFormatter.headerHint(getString(R.string.screen_download_overview_title)),
                            TaskCenterUiFormatter.batchSummary(state.selectedFilter, state.visibleTaskCount, state.failedCount),
                        ).joinToString("\n"),
                        visibleCount = state.visibleTaskCount,
                        totalCount = state.allTaskCount,
                        statsPrefix = getString(R.string.screen_download_overview_hint),
                        stats = state.combinedStats,
                    )
                    bindActionBlock(
                        actionBinding = binding.actionBlock,
                        uiState = TaskCenterActionUiState(
                            centerName = getString(R.string.screen_download_center_name),
                            scopeHint = getString(R.string.screen_download_controls_hint),
                            selectedFilter = state.selectedFilter,
                            secondaryText = getString(R.string.screen_download_retry_failed_format, state.failedCount),
                            tertiaryText = getString(R.string.screen_download_install_ready_format, state.readyInstallCount),
                            quaternaryText = getString(R.string.screen_download_clear_completed),
                            runnableCount = state.readyInstallCount,
                            failedCount = state.failedCount,
                        ),
                    )
                    // 失败面板和空态面板根据当前筛选结果动态切换。
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
                            showEmpty = state.visibleTaskCount == 0,
                            showSecondary = true,
                        ),
                    )
                    bindListBlock(
                        listBlockBinding = binding.installTaskBlock,
                        sectionName = getString(R.string.screen_install_tasks),
                        visible = state.installTasks.isNotEmpty(),
                    )
                    bindListBlock(
                        listBlockBinding = binding.downloadTaskBlock,
                        sectionName = getString(R.string.screen_download_tasks),
                        visible = state.tasks.isNotEmpty(),
                    )

                    // 扩展控制区和双列表会随同一份 UI 状态一起刷新。
                    preferencesController?.bind(state.preferencesUiState)

                    installAdapter.submitList(state.installTasks)
                    downloadAdapter.submitList(state.tasks)
                }
            }
        }
    }

    /** 释放下载中心页面相关引用。 */
    override fun onDestroyView() {
        super.onDestroyView()
        preferencesController = null
        preferencesBinding = null
        _binding = null
    }

    companion object {
        /** 创建下载中心实例。 */
        fun newInstance() = DownloadManagerFragment()
    }
}
