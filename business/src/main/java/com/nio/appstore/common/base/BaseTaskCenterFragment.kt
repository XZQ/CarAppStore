package com.nio.appstore.common.base

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nio.appstore.common.ui.TaskCenterUiFormatter
import com.nio.appstore.data.model.TaskCenterActionUiState
import com.nio.appstore.data.model.TaskCenterEmptyUiState
import com.nio.appstore.data.model.TaskCenterFailureUiState
import com.nio.appstore.data.model.TaskCenterExtensionUiState
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.common.databinding.ViewTaskCenterActionsBinding
import com.nio.appstore.common.databinding.ViewTaskCenterEmptyPanelBinding
import com.nio.appstore.common.databinding.ViewTaskCenterFailurePanelBinding
import com.nio.appstore.common.databinding.ViewTaskCenterExtensionSlotBinding
import com.nio.appstore.common.databinding.ViewTaskCenterHeaderBinding
import com.nio.appstore.common.databinding.ViewTaskCenterListBlockBinding

abstract class BaseTaskCenterFragment : BaseFragment() {

    protected fun bindCenterTitle(
        headerBinding: ViewTaskCenterHeaderBinding,
        title: String,
    ) {
        navigator.updateTitle(title)
        headerBinding.tvCenterTitle.text = title
    }

    protected fun setupTaskRecycler(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<*>,
    ) {
        if (recyclerView.layoutManager == null) {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
        }
        recyclerView.adapter = adapter
    }

    protected fun setupListBlock(
        listBlockBinding: ViewTaskCenterListBlockBinding,
        adapter: RecyclerView.Adapter<*>,
        sectionName: String,
    ) {
        bindListBlock(listBlockBinding, sectionName, visible = true)
        setupTaskRecycler(listBlockBinding.recyclerTaskList, adapter)
    }

    protected fun bindHeaderBlock(
        headerBinding: ViewTaskCenterHeaderBinding,
        centerName: String,
        subtitle: String,
        hint: String,
        visibleCount: Int,
        totalCount: Int,
        statsPrefix: String,
        stats: TaskCenterStats,
    ) {
        headerBinding.tvCenterSubtitle.text = subtitle
        headerBinding.tvCenterHint.text = hint
        headerBinding.tvCenterSummary.text =
            TaskCenterUiFormatter.centerSummary(centerName, visibleCount, totalCount)
        headerBinding.tvStatActive.text =
            TaskCenterUiFormatter.compactStatTitle("执行中", stats.activeCount)
        headerBinding.tvStatPending.text =
            TaskCenterUiFormatter.compactStatTitle("待处理", stats.pendingCount)
        headerBinding.tvStatFailed.text =
            TaskCenterUiFormatter.compactStatTitle("失败", stats.failedCount)
        headerBinding.tvStatCompleted.text =
            TaskCenterUiFormatter.compactStatTitle("完成", stats.completedCount)
        headerBinding.tvStatSummary.text = TaskCenterUiFormatter.statsLine(statsPrefix, stats)
    }

    protected fun bindActionBlock(
        actionBinding: ViewTaskCenterActionsBinding,
        centerName: String,
        scopeHint: String,
        selectedFilter: TaskCenterFilter,
        secondaryText: String,
        tertiaryText: String,
        quaternaryText: String? = null,
        runnableCount: Int,
        failedCount: Int,
    ) {
        actionBinding.tvActionTitle.text = TaskCenterUiFormatter.actionTitle(centerName)
        actionBinding.tvActionHint.text = TaskCenterUiFormatter.actionHint(centerName, scopeHint)
        actionBinding.btnActionPrimary.text = TaskCenterUiFormatter.filterButtonText(selectedFilter)
        actionBinding.btnActionSecondary.text = secondaryText
        actionBinding.btnActionTertiary.text = tertiaryText
        if (quaternaryText == null) {
            actionBinding.btnActionQuaternary.visibility = View.GONE
        } else {
            actionBinding.btnActionQuaternary.visibility = View.VISIBLE
            actionBinding.btnActionQuaternary.text = quaternaryText
        }
        actionBinding.tvActionSummary.text =
            TaskCenterUiFormatter.batchActionSummary(runnableCount, failedCount)
    }

    protected fun bindFailurePanel(
        failureBinding: ViewTaskCenterFailurePanelBinding,
        centerName: String,
        failedCount: Int,
        primaryText: String,
        secondaryText: String,
        showPanel: Boolean,
        showSecondary: Boolean = true,
    ) {
        failureBinding.root.visibility = if (showPanel) View.VISIBLE else View.GONE
        failureBinding.tvFailureTitle.text =
            TaskCenterUiFormatter.failurePanelTitle(centerName, failedCount)
        failureBinding.tvFailureDesc.text =
            TaskCenterUiFormatter.failurePanelMessage(centerName)
        failureBinding.tvFailureHint.text =
            TaskCenterUiFormatter.failurePanelHint(centerName)
        failureBinding.btnFailurePrimary.text = primaryText
        failureBinding.btnFailureSecondary.text = secondaryText
        failureBinding.btnFailureSecondary.visibility = if (showSecondary) View.VISIBLE else View.GONE
    }

    protected fun bindEmptyPanel(
        emptyPanelBinding: ViewTaskCenterEmptyPanelBinding,
        centerName: String,
        selectedFilter: TaskCenterFilter,
        showEmpty: Boolean,
        secondaryVisible: Boolean = true,
    ) {
        emptyPanelBinding.root.visibility = if (showEmpty) View.VISIBLE else View.GONE
        emptyPanelBinding.tvEmptyTitle.text =
            TaskCenterUiFormatter.emptyTitle(centerName, selectedFilter)
        emptyPanelBinding.tvEmptyDesc.text =
            TaskCenterUiFormatter.emptyMessage(centerName, selectedFilter)
        emptyPanelBinding.tvEmptyHint.text =
            TaskCenterUiFormatter.emptyPanelHint(centerName)
        emptyPanelBinding.btnEmptySecondary.visibility =
            if (secondaryVisible) View.VISIBLE else View.GONE
    }

    protected fun bindListBlock(
        listBlockBinding: ViewTaskCenterListBlockBinding,
        sectionName: String,
        visible: Boolean,
    ) {
        listBlockBinding.tvSectionTitle.text =
            TaskCenterUiFormatter.sectionTitle(sectionName)
        listBlockBinding.tvSectionHint.text =
            TaskCenterUiFormatter.sectionHint(sectionName)
        listBlockBinding.root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    protected fun bindActionBlock(
        actionBinding: ViewTaskCenterActionsBinding,
        uiState: TaskCenterActionUiState,
    ) {
        bindActionBlock(
            actionBinding = actionBinding,
            centerName = uiState.centerName,
            scopeHint = uiState.scopeHint,
            selectedFilter = uiState.selectedFilter,
            secondaryText = uiState.secondaryText,
            tertiaryText = uiState.tertiaryText,
            quaternaryText = uiState.quaternaryText,
            runnableCount = uiState.runnableCount,
            failedCount = uiState.failedCount,
        )
    }

    protected fun bindFailurePanel(
        failureBinding: ViewTaskCenterFailurePanelBinding,
        uiState: TaskCenterFailureUiState,
    ) {
        bindFailurePanel(
            failureBinding = failureBinding,
            centerName = uiState.centerName,
            failedCount = uiState.failedCount,
            primaryText = uiState.primaryText,
            secondaryText = uiState.secondaryText,
            showPanel = uiState.showPanel,
            showSecondary = uiState.showSecondary,
        )
    }

    protected fun bindEmptyPanel(
        emptyPanelBinding: ViewTaskCenterEmptyPanelBinding,
        uiState: TaskCenterEmptyUiState,
    ) {
        bindEmptyPanel(
            emptyPanelBinding = emptyPanelBinding,
            centerName = uiState.centerName,
            selectedFilter = uiState.selectedFilter,
            showEmpty = uiState.showEmpty,
            secondaryVisible = uiState.showSecondary,
        )
        emptyPanelBinding.btnEmptyPrimary.text = uiState.primaryText
        emptyPanelBinding.btnEmptySecondary.text = uiState.secondaryText
    }


    protected fun bindExtensionSlot(
        extensionBinding: ViewTaskCenterExtensionSlotBinding,
        uiState: TaskCenterExtensionUiState,
    ) {
        extensionBinding.root.visibility = if (uiState.showPanel) View.VISIBLE else View.GONE
        extensionBinding.tvExtensionTitle.text =
            TaskCenterUiFormatter.extensionTitle(uiState.centerName, uiState.title)
        extensionBinding.tvExtensionHint.text =
            TaskCenterUiFormatter.extensionHint(uiState.centerName, uiState.hint)
    }

    protected fun attachExtensionContent(
        extensionBinding: ViewTaskCenterExtensionSlotBinding,
        contentView: View,
    ) {
        val currentParent = contentView.parent
        if (currentParent is android.view.ViewGroup) {
            currentParent.removeView(contentView)
        }
        extensionBinding.extensionContentContainer.removeAllViews()
        extensionBinding.extensionContentContainer.addView(contentView)
    }

    protected fun bindActionHandlers(
        actionBinding: ViewTaskCenterActionsBinding,
        onPrimary: () -> Unit,
        onSecondary: () -> Unit,
        onTertiary: () -> Unit,
        onQuaternary: (() -> Unit)? = null,
    ) {
        actionBinding.btnActionPrimary.setOnClickListener { onPrimary() }
        actionBinding.btnActionSecondary.setOnClickListener { onSecondary() }
        actionBinding.btnActionTertiary.setOnClickListener { onTertiary() }
        actionBinding.btnActionQuaternary.setOnClickListener { onQuaternary?.invoke() }
    }

    protected fun bindFailureHandlers(
        failureBinding: ViewTaskCenterFailurePanelBinding,
        onPrimary: () -> Unit,
        onSecondary: (() -> Unit)? = null,
    ) {
        failureBinding.btnFailurePrimary.setOnClickListener { onPrimary() }
        failureBinding.btnFailureSecondary.setOnClickListener { onSecondary?.invoke() }
    }

    protected fun bindEmptyHandlers(
        emptyPanelBinding: ViewTaskCenterEmptyPanelBinding,
        onPrimary: () -> Unit,
        onSecondary: (() -> Unit)? = null,
    ) {
        emptyPanelBinding.btnEmptyPrimary.setOnClickListener { onPrimary() }
        emptyPanelBinding.btnEmptySecondary.setOnClickListener { onSecondary?.invoke() }
    }
}
