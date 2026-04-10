
package com.nio.appstore.feature.downloadmanager

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
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.feature.downloadmanager.databinding.ItemDownloadTaskBinding

class DownloadTaskAdapter(
    /** 主动作按钮点击回调。 */
    private val onPrimaryClick: (DownloadTaskViewData) -> Unit,
    /** 次动作按钮点击回调。 */
    private val onSecondaryClick: (DownloadTaskViewData) -> Unit,
    /** 详情按钮点击回调。 */
    private val onDetailClick: (DownloadTaskViewData) -> Unit,
) : ListAdapter<DownloadTaskViewData, DownloadTaskAdapter.TaskViewHolder>(DiffCallback) {

    /** 创建下载任务卡片 ViewHolder。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemDownloadTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    /** 绑定下载任务卡片数据。 */
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TaskViewHolder(
        /** 下载任务卡片的 ViewBinding。 */
        private val binding: ItemDownloadTaskBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        /** 把下载任务数据渲染到卡片。 */
        fun bind(item: DownloadTaskViewData) {
            binding.layoutTaskCard.applyTaskCardBackground(item.overallStatus)
            binding.tvTaskName.text = item.name
            binding.tvTaskVersion.text = binding.root.context.getString(R.string.task_download_version_format, item.versionName)
            binding.tvTaskState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvTaskBucket.applyTagStyle(
                CarUiStyle.tagStyle(
                    CarUiStyle.taskBucketText(item.overallStatus),
                    CarUiStyle.taskBucketTone(item.overallStatus),
                ),
            )
            binding.tvTaskSummary.text = buildSummary(item)
            binding.tvTaskSize.text = binding.root.context.getString(R.string.task_download_size_format, item.sizeText)
            binding.tvTaskSpeed.text = binding.root.context.getString(R.string.task_download_speed_format, item.speedText)
            binding.tvTaskTime.text = item.timeText
            binding.tvTaskPath.text = item.pathText
            binding.tvTaskProgress.text = binding.root.context.getString(R.string.task_download_progress_format, item.progress)
            binding.progressTask.progress = item.progress
            // 进度条只在下载进行中展示，避免完成态仍显示进度组件。
            binding.progressTask.visibility = if (item.progress > 0 && item.progress < 100) View.VISIBLE else View.GONE
            binding.tvTaskProgress.visibility = if (item.progress > 0 || item.overallStatus.name == "ACTIVE") View.VISIBLE else View.GONE
            binding.tvTaskReason.visibility = if (item.reasonText.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvTaskReason.text = item.reasonText

            binding.btnTaskPrimary.applyActionStyle(CarUiStyle.actionStyle(item.primaryAction))
            binding.btnTaskPrimary.setOnClickListener { onPrimaryClick(item) }
            binding.btnTaskSecondary.text = item.secondaryActionText
            binding.btnTaskSecondary.visibility = if (item.showSecondaryAction) View.VISIBLE else View.GONE
            binding.btnTaskSecondary.setOnClickListener { onSecondaryClick(item) }
            binding.btnTaskDetail.setOnClickListener { onDetailClick(item) }
        }

        /** 根据任务分组生成下载任务摘要。 */
        private fun buildSummary(item: DownloadTaskViewData): String {
            val stage = when (item.overallStatus) {
                com.nio.appstore.data.model.TaskOverallStatus.ACTIVE -> binding.root.context.getString(R.string.task_download_summary_active)
                com.nio.appstore.data.model.TaskOverallStatus.PENDING -> binding.root.context.getString(R.string.task_download_summary_pending)
                com.nio.appstore.data.model.TaskOverallStatus.FAILED -> binding.root.context.getString(R.string.task_download_summary_failed)
                com.nio.appstore.data.model.TaskOverallStatus.COMPLETED -> if (item.installed) binding.root.context.getString(R.string.task_download_summary_completed_open) else binding.root.context.getString(R.string.task_download_summary_completed_install)
            }
            return stage
        }
    }

    companion object {
        /** 下载任务列表差异比较器。 */
        private val DiffCallback = object : DiffUtil.ItemCallback<DownloadTaskViewData>() {
            override fun areItemsTheSame(oldItem: DownloadTaskViewData, newItem: DownloadTaskViewData): Boolean = oldItem.appId == newItem.appId
            override fun areContentsTheSame(oldItem: DownloadTaskViewData, newItem: DownloadTaskViewData): Boolean = oldItem == newItem
        }
    }
}
