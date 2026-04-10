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
import com.nio.appstore.common.ui.CommonUiText

abstract class BaseTaskCenterFragment : BaseFragment() {

    /** 绑定任务中心标题，并同步更新宿主页面标题。 */
    protected fun bindCenterTitle(headerBinding: ViewTaskCenterHeaderBinding, title: String) {
        navigator.updateTitle(title)
        headerBinding.tvCenterTitle.text = title
    }

    /** 初始化任务列表的布局管理器和适配器。 */
    protected fun setupTaskRecycler(recyclerView: RecyclerView, adapter: RecyclerView.Adapter<*>) {
        if (recyclerView.layoutManager == null) {
            // 任务中心统一使用纵向列表布局。
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
        }
        recyclerView.adapter = adapter
    }

    /** 绑定任务列表区块标题，并挂接对应适配器。 */
    protected fun setupListBlock(listBlockBinding: ViewTaskCenterListBlockBinding, adapter: RecyclerView.Adapter<*>, sectionName: String) {
        bindListBlock(listBlockBinding, sectionName, visible = true)
        setupTaskRecycler(listBlockBinding.recyclerTaskList, adapter)
    }

    /** 绑定任务中心头部摘要区。 */
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
        // 头部区同时展示描述信息和当前统计摘要。
        headerBinding.tvCenterSubtitle.text = subtitle
        headerBinding.tvCenterHint.text = hint
        headerBinding.tvCenterSummary.text = TaskCenterUiFormatter.centerSummary(centerName, visibleCount, totalCount)
        headerBinding.tvStatActive.text = TaskCenterUiFormatter.compactStatTitle(CommonUiText.TASK_ACTIVE, stats.activeCount)
        headerBinding.tvStatPending.text = TaskCenterUiFormatter.compactStatTitle(CommonUiText.TASK_PENDING, stats.pendingCount)
        headerBinding.tvStatFailed.text = TaskCenterUiFormatter.compactStatTitle(CommonUiText.TASK_FAILED, stats.failedCount)
        headerBinding.tvStatCompleted.text = TaskCenterUiFormatter.compactStatTitle(CommonUiText.TASK_COMPLETED, stats.completedCount)
        headerBinding.tvStatSummary.text = TaskCenterUiFormatter.statsLine(statsPrefix, stats)
    }

    /** 绑定批量动作区的文案和显隐状态。 */
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
        // 先刷新动作区的文案，再根据是否有第四按钮决定显隐。
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
        actionBinding.tvActionSummary.text = TaskCenterUiFormatter.batchActionSummary(runnableCount, failedCount)
    }

    /** 绑定失败面板的文案和显隐状态。 */
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
        // 失败面板只负责展示当前失败批次的集中处理入口。
        failureBinding.tvFailureTitle.text = TaskCenterUiFormatter.failurePanelTitle(centerName, failedCount)
        failureBinding.tvFailureDesc.text = TaskCenterUiFormatter.failurePanelMessage(centerName)
        failureBinding.tvFailureHint.text = TaskCenterUiFormatter.failurePanelHint(centerName)
        failureBinding.btnFailurePrimary.text = primaryText
        failureBinding.btnFailureSecondary.text = secondaryText
        failureBinding.btnFailureSecondary.visibility = if (showSecondary) View.VISIBLE else View.GONE
    }

    /** 绑定空态面板的文案和显隐状态。 */
    protected fun bindEmptyPanel(
            emptyPanelBinding: ViewTaskCenterEmptyPanelBinding,
            centerName: String,
            selectedFilter: TaskCenterFilter,
            showEmpty: Boolean,
            secondaryVisible: Boolean = true,
    ) {
        emptyPanelBinding.root.visibility = if (showEmpty) View.VISIBLE else View.GONE
        // 空态文案会跟随当前筛选条件变化。
        emptyPanelBinding.tvEmptyTitle.text = TaskCenterUiFormatter.emptyTitle(centerName, selectedFilter)
        emptyPanelBinding.tvEmptyDesc.text = TaskCenterUiFormatter.emptyMessage(centerName, selectedFilter)
        emptyPanelBinding.tvEmptyHint.text = TaskCenterUiFormatter.emptyPanelHint(centerName)
        emptyPanelBinding.btnEmptySecondary.visibility = if (secondaryVisible) View.VISIBLE else View.GONE
    }

    /** 绑定列表区块标题和显隐状态。 */
    protected fun bindListBlock(
            listBlockBinding: ViewTaskCenterListBlockBinding,
            sectionName: String,
            visible: Boolean,
    ) {
        listBlockBinding.tvSectionTitle.text = TaskCenterUiFormatter.sectionTitle(sectionName)
        listBlockBinding.tvSectionHint.text = TaskCenterUiFormatter.sectionHint(sectionName)
        listBlockBinding.root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** 使用动作区 UI 状态对象批量绑定动作区。 */
    protected fun bindActionBlock(actionBinding: ViewTaskCenterActionsBinding, uiState: TaskCenterActionUiState) {
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

    /** 使用失败区 UI 状态对象批量绑定失败面板。 */
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

    /** 使用空态 UI 状态对象批量绑定空态面板。 */
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


    /** 绑定扩展插槽的标题、提示和显隐状态。 */
    protected fun bindExtensionSlot(
            extensionBinding: ViewTaskCenterExtensionSlotBinding,
            uiState: TaskCenterExtensionUiState,
    ) {
        extensionBinding.root.visibility = if (uiState.showPanel) View.VISIBLE else View.GONE
        extensionBinding.tvExtensionTitle.text = TaskCenterUiFormatter.extensionTitle(uiState.centerName, uiState.title)
        extensionBinding.tvExtensionHint.text = TaskCenterUiFormatter.extensionHint(uiState.centerName, uiState.hint)
    }

    /** 将扩展内容视图挂接到任务中心插槽中。 */
    protected fun attachExtensionContent(
            extensionBinding: ViewTaskCenterExtensionSlotBinding,
            contentView: View,
    ) {
        val currentParent = contentView.parent
        if (currentParent is android.view.ViewGroup) {
            // 同一个 View 只能有一个父容器，先从旧容器移除。
            currentParent.removeView(contentView)
        }
        extensionBinding.extensionContentContainer.removeAllViews()
        extensionBinding.extensionContentContainer.addView(contentView)
    }

    /** 绑定动作区按钮点击事件。 */
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

    /** 绑定失败面板按钮点击事件。 */
    protected fun bindFailureHandlers(
            failureBinding: ViewTaskCenterFailurePanelBinding,
            onPrimary: () -> Unit,
            onSecondary: (() -> Unit)? = null,
    ) {
        failureBinding.btnFailurePrimary.setOnClickListener { onPrimary() }
        failureBinding.btnFailureSecondary.setOnClickListener { onSecondary?.invoke() }
    }

    /** 绑定空态面板按钮点击事件。 */
    protected fun bindEmptyHandlers(
            emptyPanelBinding: ViewTaskCenterEmptyPanelBinding,
            onPrimary: () -> Unit,
            onSecondary: (() -> Unit)? = null,
    ) {
        emptyPanelBinding.btnEmptyPrimary.setOnClickListener { onPrimary() }
        emptyPanelBinding.btnEmptySecondary.setOnClickListener { onSecondary?.invoke() }
    }
}
