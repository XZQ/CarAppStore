package com.xzq.appstore.common.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xzq.appstore.common.R
import com.xzq.appstore.common.databinding.ItemCatalogAppBinding
import com.xzq.appstore.data.model.AppViewData

/** 首页与目录共用的卡片呈现；只绑定快照和上报点击，不读写业务数据。 */
open class CatalogAppAdapter(
    private val onPrimaryClick: (AppViewData) -> Unit,
    private val onDetailClick: (AppViewData) -> Unit,
    private val showRank: Boolean = false,
    private val compact: Boolean = false,
) : ListAdapter<AppViewData, CatalogAppAdapter.Holder>(Diff) {
    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCatalogAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        if (compact || parent.resources.configuration.fontScale >= 1.5f) {
            val density = parent.resources.displayMetrics.density
            if (compact) {
                binding.root.layoutParams.width = (144 * density * parent.resources.configuration.fontScale.coerceAtLeast(1f)).toInt()
                (binding.root.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = (10 * density).toInt()
            }
            binding.cardBody.orientation = LinearLayout.VERTICAL
            binding.cardBody.gravity = Gravity.CENTER_HORIZONTAL
            binding.cardText.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            binding.tvName.gravity = Gravity.CENTER
            if (compact) binding.tvDescription.visibility = View.GONE
        }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), position, false)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        holder.bind(getItem(position), position, payloads.isNotEmpty() && payloads.all { it == STATUS_ONLY })
    }

    override fun onCurrentListChanged(previousList: MutableList<AppViewData>, currentList: MutableList<AppViewData>) {
        super.onCurrentListChanged(previousList, currentList)
        if (showRank && previousList.map { it.appId } != currentList.map { it.appId }) {
            notifyItemRangeChanged(0, currentList.size, STATUS_ONLY)
        }
    }

    inner class Holder(private val binding: ItemCatalogAppBinding) : RecyclerView.ViewHolder(binding.root) {
        private var item: AppViewData? = null
        init {
            binding.root.setOnClickListener { item?.let(onDetailClick) }
            binding.btnPrimary.setOnClickListener { item?.let(onPrimaryClick) }
        }

        fun bind(value: AppViewData, position: Int, statusOnly: Boolean) {
            item = value
            if (!statusOnly) {
                binding.tvName.text = value.name
                binding.tvDescription.text = if (showRank && value.rating != null) {
                    binding.root.context.getString(R.string.catalog_rating_format, value.rating)
                } else value.description
                binding.tvInitial.text = value.iconText.ifBlank { value.name.firstOrNull()?.toString().orEmpty() }
                AppImageLoader.load(binding.ivIcon, value.iconUrl, binding.tvInitial)
            }
            binding.tvRank.visibility = if (showRank) View.VISIBLE else View.GONE
            binding.tvRank.text = (position + 1).toString()
            binding.btnPrimary.applyActionStyle(CarUiStyle.actionStyle(value.primaryAction))
            binding.btnPrimary.contentDescription = binding.root.context.getString(R.string.catalog_action_description, value.name, binding.btnPrimary.text)
            binding.tvState.text = value.stateText
            binding.progressDownload.progress = value.progress
            binding.progressDownload.visibility = if (value.progress in 1..99) View.VISIBLE else View.GONE
        }
    }

    internal object Diff : DiffUtil.ItemCallback<AppViewData>() {
        override fun areItemsTheSame(oldItem: AppViewData, newItem: AppViewData) = oldItem.appId == newItem.appId
        override fun areContentsTheSame(oldItem: AppViewData, newItem: AppViewData) = oldItem == newItem
        override fun getChangePayload(oldItem: AppViewData, newItem: AppViewData): Any? =
            if (oldItem.copy(stateText = newItem.stateText, statusTone = newItem.statusTone, primaryAction = newItem.primaryAction,
                    progress = newItem.progress, installed = newItem.installed) == newItem) STATUS_ONLY else null
    }

    private companion object { const val STATUS_ONLY = "status" }
}
