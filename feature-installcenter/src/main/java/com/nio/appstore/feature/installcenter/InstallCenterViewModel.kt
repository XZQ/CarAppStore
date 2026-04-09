package com.nio.appstore.feature.installcenter

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.data.model.InstallCenterControlsUiState
import com.nio.appstore.data.model.InstallSessionFilter
import com.nio.appstore.data.model.SessionBucket
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.StateCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class InstallCenterViewModel(
    private val appManager: AppManager,
    private val stateCenter: StateCenter,
    private val installManager: InstallManager,
    private val installSessionStore: InstallSessionStore,
) : BaseViewModel<InstallCenterUiState>(InstallCenterUiState()) {

    private var observeJob: Job? = null
    private var selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL
    private var selectedSessionFilter: InstallSessionFilter = InstallSessionFilter.ALL

    fun load() {
        viewModelScope.launch {
            refresh()
            observeStateChanges()
        }
    }

    fun onPrimaryClick(appId: String, action: PrimaryAction) {
        viewModelScope.launch {
            when (action) {
                PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> installManager.install(appId)
                else -> Unit
            }
            refresh()
        }
    }

    fun onCycleFilter() {
        selectedFilter = selectedFilter.next()
        viewModelScope.launch { refresh() }
    }

    fun onCycleSessionFilter() {
        selectedSessionFilter = selectedSessionFilter.next()
        viewModelScope.launch { refresh() }
    }

    fun onBatchStartRunnable() {
        viewModelScope.launch {
            appManager.getInstallTasks()
                .filter { selectedFilter.matches(it.overallStatus) }
                .filter { selectedSessionFilter.matches(it.sessionBucket) }
                .filter { it.primaryAction == PrimaryAction.INSTALL || it.primaryAction == PrimaryAction.RETRY_INSTALL }
                .forEach { installManager.install(it.appId) }
            refresh()
        }
    }

    fun onRetryFailed() {
        viewModelScope.launch {
            appManager.getInstallTasks()
                .filter { selectedSessionFilter.matches(it.sessionBucket) }
                .filter { it.primaryAction == PrimaryAction.RETRY_INSTALL }
                .forEach { installManager.install(it.appId) }
            refresh()
        }
    }

    fun onRetryRetryableSessions() {
        viewModelScope.launch {
            installSessionStore.getRetryableSessions()
                .map { it.appId }
                .distinct()
                .forEach { installManager.install(it) }
            refresh()
        }
    }

    fun onClearFailed() {
        viewModelScope.launch {
            installSessionStore.clearFailedSessions()
            appManager.getInstallTasks()
                .filter { it.reasonText != null }
                .forEach { installManager.clearFailed(it.appId) }
            refresh()
        }
    }


    private data class InstallCenterSummary(
        val failedCount: Int,
        val runnableCount: Int,
        val recoveredSessionCount: Int,
        val retryableSessionCount: Int,
    )

    private fun summarize(
        allTasks: List<com.nio.appstore.data.model.InstallTaskViewData>,
        visible: List<com.nio.appstore.data.model.InstallTaskViewData>,
    ): InstallCenterSummary {
        val failedCount = allTasks.count { !it.reasonText.isNullOrBlank() }
        val sessionFailedCount = allTasks.count { it.sessionBucket == SessionBucket.FAILED }
        val recoveredInterruptedCount = allTasks.count { it.sessionBucket == SessionBucket.RECOVERED }
        val runnableCount = visible.count { it.primaryAction == PrimaryAction.INSTALL || it.primaryAction == PrimaryAction.RETRY_INSTALL }
        return InstallCenterSummary(
            failedCount = failedCount,
            runnableCount = runnableCount,
            recoveredSessionCount = recoveredInterruptedCount,
            retryableSessionCount = sessionFailedCount + recoveredInterruptedCount,
        )
    }

    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    private suspend fun refresh() {
        val allTasks = appManager.getInstallTasks()
        val visible = allTasks
            .filter { selectedFilter.matches(it.overallStatus) }
            .filter { selectedSessionFilter.matches(it.sessionBucket) }

        val summary = summarize(allTasks, visible)

        _uiState.value = InstallCenterUiState(
            tasks = visible,
            allTaskCount = allTasks.size,
            failedCount = summary.failedCount,
            stats = appManager.getInstallTaskStats(),
            selectedFilter = selectedFilter,
            selectedSessionFilter = selectedSessionFilter,
            batchRunnableCount = summary.runnableCount,
            clearFailedCount = summary.failedCount,
            controlsUiState = InstallCenterControlsUiState(
                runnableCount = summary.runnableCount,
                failedCount = summary.failedCount + summary.recoveredSessionCount,
                retryableSessionCount = summary.retryableSessionCount,
                recoveredSessionCount = summary.recoveredSessionCount,
            ),
        )
    }
}
