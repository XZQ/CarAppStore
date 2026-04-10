package com.nio.appstore.feature.myapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter

class MyAppViewModelFactory(
    /** 提供我的应用页聚合数据。 */
    private val appManager: AppManager,
    /** 提供我的应用页实时状态。 */
    private val stateCenter: StateCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    /** 创建我的应用页 ViewModel。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MyAppViewModel(appManager, stateCenter) as T
    }
}
