package com.xzq.appstore.common.base

import androidx.recyclerview.widget.DiffUtil

/**
 * 任务中心各列表适配器通用的差异比较器基类。
 *
 * 以业务稳定主键（如 appId）判定是否为同一项，并以数据类的结构性相等判定内容是否变化，
 * 避免下载 / 安装 / 升级三个适配器重复编写几乎一致的 [DiffUtil.ItemCallback] 实现。
 */
abstract class AppIdDiffCallback<T : Any>(
    private val idOf: (T) -> String,
    /** 由具体数据类型定义内容比较规则，避免泛型层错误假定 equals 语义。 */
    private val contentsSame: (T, T) -> Boolean,
) : DiffUtil.ItemCallback<T>() {
    final override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = idOf(oldItem) == idOf(newItem)

    final override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = contentsSame(oldItem, newItem)
}
