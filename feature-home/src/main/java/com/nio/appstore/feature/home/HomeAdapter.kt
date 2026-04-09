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
            binding.tvAppVersion.text = "版本 ${item.versionName}"
            binding.tvAppState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvPrimaryAction.applyTagStyle(CarUiStyle.tagStyle(CarUiStyle.actionStyle(item.primaryAction).text, item.statusTone))
            binding.progressDownload.progress = item.progress
            binding.tvProgress.text = if (item.progress > 0) "当前进度 ${item.progress}%" else "当前无下载进度"
            val showProgress = item.progress > 0
            binding.progressDownload.visibility = if (showProgress) View.VISIBLE else View.GONE
            binding.tvProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
            binding.btnDetail.text = if (item.installed) "进入详情 / 打开" else "进入详情"
            binding.btnDetail.setOnClickListener { onDetailClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppViewData>() {
        override fun areItemsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem.appId == newItem.appId
        override fun areContentsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem == newItem
    }
}
