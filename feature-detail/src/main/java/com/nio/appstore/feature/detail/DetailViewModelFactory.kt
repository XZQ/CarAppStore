package com.nio.appstore.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager

class DetailViewModelFactory(
    private val appManager: AppManager,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val upgradeManager: UpgradeManager,
    private val stateCenter: StateCenter,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetailViewModel(appManager, downloadManager, installManager, upgradeManager, stateCenter) as T
    }
}
