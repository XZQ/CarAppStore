
package com.nio.appstore.feature.upgrade

import com.nio.appstore.common.R

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.common.ui.applyActionStyle
import com.nio.appstore.common.ui.applyTagStyle
import com.nio.appstore.common.ui.applyTaskCardBackground
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.feature.upgrade.databinding.ItemUpgradeTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpgradeTaskAdapter(
    /** 主动作按钮点击回调。 */
    private val onPrimaryClick: (UpgradeTaskViewData) -> Unit,
    /** 详情按钮点击回调。 */
    private val onDetailClick: (UpgradeTaskViewData) -> Unit,
) : ListAdapter<UpgradeTaskViewData, UpgradeTaskAdapter.TaskViewHolder>(DiffCallback) {

    /** 创建升级任务卡片 ViewHolder。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemUpgradeTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    /** 绑定升级任务卡片数据。 */
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TaskViewHolder(
        /** 升级任务卡片的 ViewBinding。 */
        private val binding: ItemUpgradeTaskBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        /** 把升级任务数据渲染到卡片。 */
        fun bind(item: UpgradeTaskViewData) {
            binding.layoutUpgradeTaskCard.applyTaskCardBackground(item.overallStatus)
            binding.tvUpgradeTaskName.text = item.name
            binding.tvUpgradeTaskVersion.text = binding.root.context.getString(R.string.task_upgrade_version_transition_format, item.currentVersion, item.targetVersion)
            binding.tvUpgradeTaskState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvUpgradeTaskBucket.applyTagStyle(
                CarUiStyle.tagStyle(
                    CarUiStyle.taskBucketText(item.overallStatus),
                    CarUiStyle.taskBucketTone(item.overallStatus),
                ),
            )
            // 不同任务分组对应不同的摘要文案，帮助用户快速判断当前阶段。
            binding.tvUpgradeTaskSummary.text = when (item.overallStatus) {
                com.nio.appstore.data.model.TaskOverallStatus.ACTIVE -> binding.root.context.getString(R.string.task_upgrade_summary_active)
                com.nio.appstore.data.model.TaskOverallStatus.PENDING -> binding.root.context.getString(R.string.task_upgrade_summary_pending)
                com.nio.appstore.data.model.TaskOverallStatus.FAILED -> binding.root.context.getString(R.string.task_upgrade_summary_failed)
                com.nio.appstore.data.model.TaskOverallStatus.COMPLETED -> binding.root.context.getString(R.string.task_upgrade_summary_completed)
            }
            binding.tvUpgradeTaskTime.text = binding.root.context.getString(R.string.task_updated_time_format, SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.updatedAt)))
            binding.tvUpgradeTaskReason.visibility = if (item.reasonText.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvUpgradeTaskReason.text = item.reasonText
            binding.btnUpgradePrimary.applyActionStyle(CarUiStyle.actionStyle(item.primaryAction))
            binding.btnUpgradePrimary.setOnClickListener { onPrimaryClick(item) }
            binding.btnUpgradeDetail.setOnClickListener { onDetailClick(item) }
        }
    }

    companion object {
        /** 升级任务列表差异比较器。 */
        private val DiffCallback = object : DiffUtil.ItemCallback<UpgradeTaskViewData>() {
            override fun areItemsTheSame(oldItem: UpgradeTaskViewData, newItem: UpgradeTaskViewData): Boolean = oldItem.appId == newItem.appId
            override fun areContentsTheSame(oldItem: UpgradeTaskViewData, newItem: UpgradeTaskViewData): Boolean = oldItem == newItem
        }
    }
}
