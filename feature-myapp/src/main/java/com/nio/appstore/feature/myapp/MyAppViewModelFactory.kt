package com.nio.appstore.feature.myapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter

class MyAppViewModelFactory(private val appManager: AppManager, private val stateCenter: StateCenter) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MyAppViewModel(appManager, stateCenter) as T
    }
}
