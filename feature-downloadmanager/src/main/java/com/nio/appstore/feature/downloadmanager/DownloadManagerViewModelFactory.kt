package com.nio.appstore.feature.downloadmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.StateCenter

class DownloadManagerViewModelFactory(
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val policyCenter: PolicyCenter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DownloadManagerViewModel(appManager, stateCenter, downloadManager, installManager, policyCenter) as T
    }
}
