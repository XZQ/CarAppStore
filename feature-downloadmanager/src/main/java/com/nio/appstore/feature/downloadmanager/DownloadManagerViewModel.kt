package com.nio.appstore.feature.downloadmanager

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.data.model.DownloadCenterPreferencesUiState
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.StateCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DownloadManagerViewModel(
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val policyCenter: PolicyCenter,
) : BaseViewModel<DownloadManagerUiState>(DownloadManagerUiState()) {

    private var observeJob: Job? = null
    private var selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL

    fun load() {
        viewModelScope.launch {
            refresh()
            observeStateChanges()
        }
    }

    fun onPrimaryClick(item: DownloadTaskViewData) {
        viewModelScope.launch {
            when (item.primaryAction) {
                PrimaryAction.DOWNLOAD, PrimaryAction.RETRY_DOWNLOAD -> downloadManager.startDownload(item.appId)
                PrimaryAction.PAUSE -> downloadManager.pauseDownload(item.appId)
                PrimaryAction.RESUME -> downloadManager.resumeDownload(item.appId)
                PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> installManager.install(item.appId)
                PrimaryAction.OPEN, PrimaryAction.UPGRADE, PrimaryAction.DISABLED -> Unit
            }
            refresh()
        }
    }

    fun onInstallPrimaryClick(item: InstallTaskViewData) {
        viewModelScope.launch {
            when (item.primaryAction) {
                PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> installManager.install(item.appId)
                PrimaryAction.OPEN -> appManager.openApp(item.packageName)
                else -> Unit
            }
            refresh()
        }
    }

    fun onSecondaryClick(item: DownloadTaskViewData) {
        viewModelScope.launch {
            downloadManager.removeTask(item.appId, clearFile = true)
            refresh()
        }
    }

    fun onCycleFilter() {
        selectedFilter = selectedFilter.next()
        viewModelScope.launch { refresh() }
    }

    fun onClearCompleted() {
        viewModelScope.launch {
            downloadManager.clearCompletedTasks()
            refresh()
        }
    }

    fun onClearFailed() {
        viewModelScope.launch {
            val failedTasks = appManager.getDownloadTasks().filter { it.overallStatus == com.nio.appstore.data.model.TaskOverallStatus.FAILED }
            failedTasks.forEach { downloadManager.removeTask(it.appId, clearFile = true) }
            refresh()
        }
    }

    fun onRetryFailed() {
        viewModelScope.launch {
            val installFailed = appManager.getInstallTasks().filter { it.primaryAction == PrimaryAction.RETRY_INSTALL }
            installFailed.forEach { installManager.install(it.appId) }
            downloadManager.retryFailedTasks()
            refresh()
        }
    }

    fun onBatchInstallReady() {
        viewModelScope.launch {
            val readyDownloads = appManager.getDownloadTasks().filter {
                it.primaryAction == PrimaryAction.INSTALL || it.primaryAction == PrimaryAction.RETRY_INSTALL
            }
            readyDownloads.forEach { installManager.install(it.appId) }
            refresh()
        }
    }

    fun onToggleAutoResume() {
        viewModelScope.launch {
            val current = downloadManager.getPreferences()
            downloadManager.updatePreferences(current.copy(autoResumeOnLaunch = !current.autoResumeOnLaunch))
            refresh()
        }
    }

    fun onToggleAutoRetry() {
        viewModelScope.launch {
            val current = downloadManager.getPreferences()
            downloadManager.updatePreferences(current.copy(autoRetryEnabled = !current.autoRetryEnabled))
            refresh()
        }
    }

    fun onToggleWifi() {
        val current = policyCenter.getSettings()
        policyCenter.updateSettings(current.copy(wifiConnected = !current.wifiConnected))
        viewModelScope.launch { refresh() }
    }

    fun onToggleParking() {
        val current = policyCenter.getSettings()
        policyCenter.updateSettings(current.copy(parkingMode = !current.parkingMode))
        viewModelScope.launch { refresh() }
    }

    fun onToggleStorage() {
        val current = policyCenter.getSettings()
        policyCenter.updateSettings(current.copy(lowStorageMode = !current.lowStorageMode))
        viewModelScope.launch { refresh() }
    }

    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    private suspend fun refresh() {
        val allTasks = appManager.getDownloadTasks()
        val allInstallTasks = appManager.getInstallTasks()
        val preferences = downloadManager.getPreferences()
        val policy = policyCenter.getSettings()
        val visibleTasks = allTasks.filter { selectedFilter.matches(it.overallStatus) }
        val visibleInstallTasks = allInstallTasks.filter { selectedFilter.matches(it.overallStatus) }

        _uiState.value = DownloadManagerUiState(
            tasks = visibleTasks,
            installTasks = visibleInstallTasks,
            allTaskCount = allTasks.size + allInstallTasks.size,
            selectedFilter = selectedFilter,
            preferencesUiState = DownloadCenterPreferencesUiState(
                autoResumeEnabled = preferences.autoResumeOnLaunch,
                autoRetryEnabled = preferences.autoRetryEnabled,
                maxAutoRetryCount = preferences.maxAutoRetryCount,
                wifiConnected = policy.wifiConnected,
                parkingMode = policy.parkingMode,
                lowStorageMode = policy.lowStorageMode,
            ),
            failedCount = allTasks.count { it.reasonText != null } + allInstallTasks.count { !it.reasonText.isNullOrBlank() },
            downloadStats = appManager.getDownloadTaskStats(),
            installStats = appManager.getInstallTaskStats(),
        )
    }
}
