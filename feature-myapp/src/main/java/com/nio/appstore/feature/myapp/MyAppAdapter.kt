package com.nio.appstore.feature.myapp

import android.view.LayoutInflater
import android.view.ViewGroup
import com.nio.appstore.common.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.common.ui.applyTagStyle
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.feature.myapp.databinding.ItemMyAppBinding

class MyAppAdapter(
    /** 点击应用卡片时的回调。 */
    private val onItemClick: (AppViewData) -> Unit,
) : ListAdapter<AppViewData, MyAppAdapter.MyAppViewHolder>(DiffCallback) {

    /** 创建我的应用列表项 ViewHolder。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyAppViewHolder {
        val binding = ItemMyAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyAppViewHolder(binding, onItemClick)
    }

    /** 绑定我的应用列表项数据。 */
    override fun onBindViewHolder(holder: MyAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MyAppViewHolder(
        /** 我的应用列表项的 ViewBinding。 */
        private val binding: ItemMyAppBinding,
        /** 点击应用卡片时的回调。 */
        private val onItemClick: (AppViewData) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** 把我的应用数据渲染到列表项。 */
        fun bind(item: AppViewData) {
            binding.tvAppName.text = item.name
            binding.tvState.applyTagStyle(CarUiStyle.tagStyle(item.stateText, item.statusTone))
            binding.tvDesc.text = binding.root.context.getString(R.string.adapter_myapp_current_version_format, item.versionName)
            binding.tvAction.applyTagStyle(
                CarUiStyle.tagStyle(
                    CarUiStyle.actionStyle(item.primaryAction).text,
                    item.statusTone,
                ),
            )
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    /** 我的应用列表差异比较器。 */
    private object DiffCallback : DiffUtil.ItemCallback<AppViewData>() {
        override fun areItemsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem.appId == newItem.appId
        override fun areContentsTheSame(oldItem: AppViewData, newItem: AppViewData): Boolean = oldItem == newItem
    }
}
