package com.xzq.appstore.feature.upgrade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xzq.appstore.common.R
import com.xzq.appstore.common.base.AppIdDiffCallback
import com.xzq.appstore.common.ui.CarUiStyle
import com.xzq.appstore.common.ui.applyActionStyle
import com.xzq.appstore.common.ui.applyTagStyle
import com.xzq.appstore.common.ui.applyTaskCardBackground
import com.xzq.appstore.data.model.UpgradeTaskViewData
import com.xzq.appstore.feature.upgrade.databinding.ItemUpgradeTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xzq.appstore.data.model.TaskOverallStatus

class UpgradeTaskAdapter(
    private val onPrimaryClick: (UpgradeTaskViewData) -> Unit,
    private val onDetailClick: (UpgradeTaskViewData) -> Unit,
) : ListAdapter<UpgradeTaskViewData, UpgradeTaskAdapter.TaskViewHolder>(DiffCallback) {

    /** 创建升级任务卡片 ViewHolder。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemUpgradeTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    /** 绑定升级任务卡片数据。 */
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TaskViewHolder(private val binding: ItemUpgradeTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        /** 把升级任务数据渲染到卡片。 */
        fun bind(item: UpgradeTaskViewData) {
            binding.layoutUpgradeTaskCard.applyTaskCardBackground(item.overallStatus)
            binding.tvUpgradeTaskName.text = item.name
            binding.tvUpgradeTaskVersion.text = binding.root.context.getString(
                R.string.task_upgrade_version_transition_format,
                item.currentVersion,
                item.targetVersion,
            )
            binding.tvUpgradeTaskState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvUpgradeTaskBucket.applyTagStyle(
                CarUiStyle.tagStyle(CarUiStyle.taskBucketText(item.overallStatus), CarUiStyle.taskBucketTone(item.overallStatus)),
            )
            // 不同任务分组对应不同的摘要文案，帮助用户快速判断当前阶段。
            binding.tvUpgradeTaskSummary.text = when (item.overallStatus) {
                TaskOverallStatus.ACTIVE -> binding.root.context.getString(R.string.task_upgrade_summary_active)
                TaskOverallStatus.PENDING -> binding.root.context.getString(R.string.task_upgrade_summary_pending)
                TaskOverallStatus.FAILED -> binding.root.context.getString(R.string.task_upgrade_summary_failed)
                TaskOverallStatus.COMPLETED -> binding.root.context.getString(R.string.task_upgrade_summary_completed)
            }
            binding.tvUpgradeTaskTime.text = binding.root.context.getString(
                R.string.task_updated_time_format,
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.updatedAt)),
            )
            binding.tvUpgradeTaskReason.visibility = if (item.reasonText.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvUpgradeTaskReason.text = item.reasonText
            binding.btnUpgradePrimary.applyActionStyle(CarUiStyle.actionStyle(item.primaryAction))
            binding.btnUpgradePrimary.setOnClickListener { onPrimaryClick(item) }
            binding.btnUpgradeDetail.setOnClickListener { onDetailClick(item) }
        }
    }

    companion object {
        /** 升级任务列表差异比较器（基于 appId 复用通用基类）。 */
        private val DiffCallback = object : AppIdDiffCallback<UpgradeTaskViewData>({ it.appId }, { old, new -> old == new }) {}
    }
}
