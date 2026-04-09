package com.nio.appstore.feature.installcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.state.StateCenter

class InstallCenterViewModelFactory(
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
    private val installManager: InstallManager,
    private val installSessionStore: InstallSessionStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InstallCenterViewModel(appManager, stateCenter, installManager, installSessionStore) as T
    }
}
