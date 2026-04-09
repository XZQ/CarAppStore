package com.nio.appstore.feature.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager

class UpgradeViewModelFactory(
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
    private val upgradeManager: UpgradeManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UpgradeViewModel(appManager, stateCenter, upgradeManager) as T
    }
}
