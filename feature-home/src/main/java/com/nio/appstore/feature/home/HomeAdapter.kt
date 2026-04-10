package com.nio.appstore.feature.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nio.appstore.common.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.common.ui.applyActionStyle
import com.nio.appstore.common.ui.applyTagStyle
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.feature.home.databinding.ItemAppCardBinding

class HomeAdapter(
    /** 点击主动作时的回调。 */
    private val onPrimaryClick: (AppViewData) -> Unit,
    /** 点击详情按钮时的回调。 */
    private val onDetailClick: (AppViewData) -> Unit,
) : ListAdapter<AppViewData, HomeAdapter.HomeViewHolder>(DiffCallback) {

    /** 创建首页应用卡片 ViewHolder。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeViewHolder {
        val binding = ItemAppCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HomeViewHolder(binding, onPrimaryClick, onDetailClick)
    }

    /** 绑定首页应用卡片数据。 */
    override fun onBindViewHolder(holder: HomeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HomeViewHolder(
        /** 首页应用卡片的 ViewBinding。 */
        private val binding: ItemAppCardBinding,
        /** 点击主动作时的回调。 */
        private val onPrimaryClick: (AppViewData) -> Unit,
        /** 点击详情按钮时的回调。 */
        private val onDetailClick: (AppViewData) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** 把应用卡片数据渲染到首页列表项。 */
        fun bind(item: AppViewData) {
            binding.tvAppName.text = item.name
            binding.tvAppDesc.text = item.description
            binding.tvAppVersion.text = binding.root.context.getString(R.string.adapter_home_version_format, item.versionName)
            binding.tvAppState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvPrimaryAction.applyActionStyle(CarUiStyle.actionStyle(item.primaryAction))
            binding.progressDownload.progress = item.progress
            // 只有存在进度时才展示下载进度信息。
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
            binding.tvPrimaryAction.setOnClickListener { onPrimaryClick(item) }
            binding.btnDetail.setOnClickListener { onDetailClick(item) }
        }
    }

    /** 首页应用列表差异比较器。 */
    private object DiffCallback : DiffUtil.ItemCallback<AppViewData>() {
        override fun areItemsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem.appId == newItem.appId
        override fun areContentsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem == newItem
    }
}
