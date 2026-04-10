package com.nio.appstore.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter

class SearchViewModelFactory(
    /** 提供搜索页聚合数据。 */
    private val appManager: AppManager,
    /** 提供搜索结果实时状态。 */
    private val stateCenter: StateCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建搜索页 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(appManager, stateCenter) as T
    }
}
