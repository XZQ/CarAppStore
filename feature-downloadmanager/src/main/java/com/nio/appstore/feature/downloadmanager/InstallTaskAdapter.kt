
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
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.feature.downloadmanager.databinding.ItemInstallTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InstallTaskAdapter(
    private val onPrimaryClick: (InstallTaskViewData) -> Unit,
    private val onDetailClick: (InstallTaskViewData) -> Unit,
) : ListAdapter<InstallTaskViewData, InstallTaskAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemInstallTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TaskViewHolder(private val binding: ItemInstallTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: InstallTaskViewData) {
            binding.layoutInstallTaskCard.applyTaskCardBackground(item.overallStatus)
            binding.tvInstallTaskName.text = item.name
            binding.tvInstallTaskVersion.text = binding.root.context.getString(R.string.task_install_target_version_format, item.versionName)
            binding.tvInstallTaskState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvInstallTaskBucket.applyTagStyle(
                CarUiStyle.tagStyle(
                    CarUiStyle.taskBucketText(item.overallStatus),
                    CarUiStyle.taskBucketTone(item.overallStatus),
                ),
            )
            binding.tvInstallTaskSummary.text = when (item.overallStatus) {
                com.nio.appstore.data.model.TaskOverallStatus.ACTIVE -> binding.root.context.getString(R.string.task_install_summary_active)
                com.nio.appstore.data.model.TaskOverallStatus.PENDING -> binding.root.context.getString(R.string.task_install_summary_pending)
                com.nio.appstore.data.model.TaskOverallStatus.FAILED -> binding.root.context.getString(R.string.task_install_summary_failed)
                com.nio.appstore.data.model.TaskOverallStatus.COMPLETED -> binding.root.context.getString(R.string.task_install_summary_completed)
            }
            binding.tvInstallTaskTime.text = binding.root.context.getString(R.string.task_updated_time_format, SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.updatedAt)))
            val hasSessionMeta = !item.sessionIdText.isNullOrBlank() || !item.sessionPhaseText.isNullOrBlank() || !item.sessionProgressText.isNullOrBlank()
            binding.layoutInstallSessionMeta.visibility = if (hasSessionMeta) View.VISIBLE else View.GONE
            binding.tvInstallTaskSessionId.text = item.sessionIdText ?: "-"
            binding.tvInstallTaskSessionPhase.text = item.sessionPhaseText ?: "-"
            binding.tvInstallTaskSessionProgress.text = item.sessionProgressText ?: "-"
            binding.tvInstallTaskReason.visibility = if (item.reasonText.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvInstallTaskReason.text = item.reasonText
            binding.btnInstallPrimary.applyActionStyle(CarUiStyle.actionStyle(item.primaryAction))
            binding.btnInstallPrimary.setOnClickListener { onPrimaryClick(item) }
            binding.btnInstallDetail.setOnClickListener { onDetailClick(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<InstallTaskViewData>() {
            override fun areItemsTheSame(oldItem: InstallTaskViewData, newItem: InstallTaskViewData): Boolean = oldItem.appId == newItem.appId
            override fun areContentsTheSame(oldItem: InstallTaskViewData, newItem: InstallTaskViewData): Boolean = oldItem == newItem
        }
    }
}
