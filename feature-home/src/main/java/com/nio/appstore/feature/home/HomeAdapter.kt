package com.nio.appstore.feature.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nio.appstore.common.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.common.ui.applyTagStyle
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.feature.home.databinding.ItemAppCardBinding

class HomeAdapter(
    private val onDetailClick: (AppViewData) -> Unit,
) : ListAdapter<AppViewData, HomeAdapter.HomeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeViewHolder {
        val binding = ItemAppCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HomeViewHolder(binding, onDetailClick)
    }

    override fun onBindViewHolder(holder: HomeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HomeViewHolder(
        private val binding: ItemAppCardBinding,
        private val onDetailClick: (AppViewData) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppViewData) {
            binding.tvAppName.text = item.name
            binding.tvAppDesc.text = item.description
            binding.tvAppVersion.text = binding.root.context.getString(R.string.adapter_home_version_format, item.versionName)
            binding.tvAppState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvPrimaryAction.applyTagStyle(CarUiStyle.tagStyle(CarUiStyle.actionStyle(item.primaryAction).text, item.statusTone))
            binding.progressDownload.progress = item.progress
            binding.tvProgress.text = if (item.progress > 0) {
                binding.root.context.getString(R.string.adapter_home_progress_format, item.progress)
            } else {
                binding.root.context.getString(R.string.adapter_home_no_progress)
            }
            val showProgress = item.progress > 0
            binding.progressDownload.visibility = if (showProgress) View.VISIBLE else View.GONE
            binding.tvProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
            binding.btnDetail.text = if (item.installed) {
                binding.root.context.getString(R.string.adapter_home_open_detail)
            } else {
                binding.root.context.getString(R.string.adapter_home_go_detail)
            }
            binding.btnDetail.setOnClickListener { onDetailClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppViewData>() {
        override fun areItemsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem.appId == newItem.appId
        override fun areContentsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem == newItem
    }
}
