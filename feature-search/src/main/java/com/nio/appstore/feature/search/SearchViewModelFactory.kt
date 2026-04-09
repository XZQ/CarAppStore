package com.nio.appstore.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter

class SearchViewModelFactory(
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(appManager, stateCenter) as T
    }
}
