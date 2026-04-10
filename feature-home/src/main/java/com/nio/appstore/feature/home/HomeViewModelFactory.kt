package com.nio.appstore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter

class HomeViewModelFactory(
    /** 提供首页应用聚合数据。 */
    private val appManager: AppManager,
    /** 提供首页应用实时状态。 */
    private val stateCenter: StateCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建首页 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(appManager, stateCenter) as T
    }
}
