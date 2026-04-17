package com.nio.appstore.feature.downloadmanager

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.data.model.DownloadCenterPreferencesUiState
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.data.model.TaskCenterStats
import com.nio.appstore.domain.action.AppPrimaryActionExecutor
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DownloadManagerViewModel(
    /** 提供任务中心聚合视图数据。 */
    private val appManager: AppManager,
    /** 用于监听全局任务状态变化。 */
    private val stateCenter: StateCenter,
    /** 下载业务入口。 */
    private val downloadManager: DownloadManager,
    /** 安装业务入口。 */
    private val installManager: InstallManager,
    /** 升级业务入口，用于安装后刷新升级可用性。 */
    private val upgradeManager: UpgradeManager,
    /** 策略设置入口。 */
    private val policyCenter: PolicyCenter,
) : BaseViewModel<DownloadManagerUiState>(DownloadManagerUiState()) {

    /** 状态订阅任务，避免重复注册全局观察。 */
    private var observeJob: Job? = null
    /** 策略订阅任务。 */
    private var observePolicyJob: Job? = null
    /** 当前选中的任务筛选条件。 */
    private var selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL

    /** 下载中心单项任务主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
    )

    /** 初始化页面数据并开始观察状态变化。 */
    fun load() {
        viewModelScope.launch {
            refresh(showLoading = true)
            observeStateChanges()
            observePolicyChanges()
        }
    }

    /** 处理下载任务主按钮点击。 */
    fun onPrimaryClick(item: DownloadTaskViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(appId = item.appId, action = item.primaryAction)
            refresh()
        }
    }

    /** 处理安装任务区主按钮点击。 */
    fun onInstallPrimaryClick(item: InstallTaskViewData) {
        viewModelScope.launch {
            primaryActionExecutor.execute(
                appId = item.appId,
                action = item.primaryAction,
                packageName = item.packageName,
            )
            refresh()
        }
    }

    /** 处理下载任务二级按钮点击。 */
    fun onSecondaryClick(item: DownloadTaskViewData) {
        viewModelScope.launch {
            downloadManager.removeTask(item.appId, clearFile = true)
            refresh()
        }
    }

    /** 切换当前筛选条件。 */
    fun onCycleFilter() {
        selectedFilter = selectedFilter.next()
        viewModelScope.launch { refresh() }
    }

    /** 清理所有已完成任务。 */
    fun onClearCompleted() {
        viewModelScope.launch {
            downloadManager.clearCompletedTasks()
            refresh()
        }
    }

    /** 清理所有失败任务。 */
    fun onClearFailed() {
        viewModelScope.launch {
            val failedTasks = appManager.getDownloadTasks().filter { it.overallStatus == com.nio.appstore.data.model.TaskOverallStatus.FAILED }
            failedTasks.forEach { downloadManager.removeTask(it.appId, clearFile = true) }
            refresh()
        }
    }

    /** 重试失败的下载和安装任务。 */
    fun onRetryFailed() {
        viewModelScope.launch {
            val installFailed = appManager.getInstallTasks().filter { it.primaryAction == PrimaryAction.RETRY_INSTALL }
            installFailed.forEach { primaryActionExecutor.execute(it.appId, it.primaryAction, it.packageName) }
            downloadManager.retryFailedTasks()
            refresh()
        }
    }

    /** 批量安装所有已下载完成的任务。 */
    fun onBatchInstallReady() {
        viewModelScope.launch {
            val readyDownloads = appManager.getDownloadTasks().filter {
                it.primaryAction == PrimaryAction.INSTALL || it.primaryAction == PrimaryAction.RETRY_INSTALL
            }
            readyDownloads.forEach { primaryActionExecutor.execute(it.appId, it.primaryAction) }
            refresh()
        }
    }

    /** 切换自动恢复开关。 */
    fun onToggleAutoResume() {
        viewModelScope.launch {
            val current = downloadManager.getPreferences()
            downloadManager.updatePreferences(current.copy(autoResumeOnLaunch = !current.autoResumeOnLaunch))
            refresh()
        }
    }

    /** 切换自动重试开关。 */
    fun onToggleAutoRetry() {
        viewModelScope.launch {
            val current = downloadManager.getPreferences()
            downloadManager.updatePreferences(current.copy(autoRetryEnabled = !current.autoRetryEnabled))
            refresh()
        }
    }

    /** 切换 Wi‑Fi 策略开关。 */
    fun onToggleWifi() {
        val current = policyCenter.getStoredSettings()
        policyCenter.updateSettings(current.copy(wifiConnected = !current.wifiConnected))
        viewModelScope.launch { refresh() }
    }

    /** 切换驻车策略开关。 */
    fun onToggleParking() {
        val current = policyCenter.getStoredSettings()
        policyCenter.updateSettings(current.copy(parkingMode = !current.parkingMode))
        viewModelScope.launch { refresh() }
    }

    /** 切换低存储策略开关。 */
    fun onToggleStorage() {
        val current = policyCenter.getStoredSettings()
        policyCenter.updateSettings(current.copy(lowStorageMode = !current.lowStorageMode))
        viewModelScope.launch { refresh() }
    }

    /** 监听全局任务状态变化，并在变化时刷新页面。 */
    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /** 监听策略变化，并在变化时刷新下载中心。 */
    private fun observePolicyChanges() {
        if (observePolicyJob != null) return
        observePolicyJob = policyCenter.observeSettings()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /** 重新计算页面所需的下载中心 UI 状态。 */
    private suspend fun refresh(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.value = _uiState.value.copy(screenState = DownloadManagerScreenState.Loading)
        }
        runCatching {
            val allTasks = appManager.getDownloadTasks()
            val allInstallTasks = appManager.getInstallTasks()
            val preferences = downloadManager.getPreferences()
            val policy = policyCenter.getSettings()
            // 先按当前筛选条件得到可见任务，再计算统计信息和开关区状态。
            val visibleTasks = allTasks.filter { selectedFilter.matches(it.overallStatus) }
            val visibleInstallTasks = allInstallTasks.filter { selectedFilter.matches(it.overallStatus) }
            val downloadStats = appManager.getDownloadTaskStats()
            val installStats = appManager.getInstallTaskStats()
            val visibleTaskCount = visibleTasks.size + visibleInstallTasks.size

            DownloadManagerUiState(
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
                downloadStats = downloadStats,
                installStats = installStats,
                readyInstallCount = visibleTasks.count {
                    it.primaryAction == PrimaryAction.INSTALL || it.primaryAction == PrimaryAction.RETRY_INSTALL
                },
                visibleTaskCount = visibleTaskCount,
                combinedStats = TaskCenterStats(
                    activeCount = downloadStats.activeCount + installStats.activeCount,
                    pendingCount = downloadStats.pendingCount + installStats.pendingCount,
                    failedCount = downloadStats.failedCount + installStats.failedCount,
                    completedCount = downloadStats.completedCount + installStats.completedCount,
                ),
                screenState = if (visibleTaskCount == 0) {
                    DownloadManagerScreenState.Empty
                } else {
                    DownloadManagerScreenState.Content
                },
            )
        }.onSuccess { _uiState.value = it }
            .onFailure { throwable ->
                _uiState.value = DownloadManagerUiState(
                    selectedFilter = selectedFilter,
                    screenState = DownloadManagerScreenState.Error(throwable.message.orEmpty()),
                )
            }
    }
}
