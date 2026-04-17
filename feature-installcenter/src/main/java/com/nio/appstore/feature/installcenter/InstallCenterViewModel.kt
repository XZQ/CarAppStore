package com.nio.appstore.feature.installcenter

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.core.installer.InstallSessionStore
import com.nio.appstore.data.model.InstallCenterControlsUiState
import com.nio.appstore.data.model.InstallSessionFilter
import com.nio.appstore.data.model.SessionBucket
import com.nio.appstore.data.model.TaskCenterFilter
import com.nio.appstore.domain.action.AppPrimaryActionExecutor
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class InstallCenterViewModel(
    /** 提供安装中心聚合任务数据。 */
    private val appManager: AppManager,
    /** 用于监听全局安装状态变化。 */
    private val stateCenter: StateCenter,
    /** 安装业务入口。 */
    private val installManager: InstallManager,
    /** 安装完成后的升级状态检查入口。 */
    private val upgradeManager: UpgradeManager,
    /** 安装会话存储，用于读取可重试会话。 */
    private val installSessionStore: InstallSessionStore,
) : BaseViewModel<InstallCenterUiState>(InstallCenterUiState()) {

    /** 状态订阅任务，避免重复注册观察。 */
    private var observeJob: Job? = null
    /** 当前选中的总状态筛选条件。 */
    private var selectedFilter: TaskCenterFilter = TaskCenterFilter.ALL
    /** 当前选中的会话筛选条件。 */
    private var selectedSessionFilter: InstallSessionFilter = InstallSessionFilter.ALL

    /** 安装中心单项任务主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
    )

    /** 初始化页面并开始观察安装状态变化。 */
    fun load() {
        viewModelScope.launch {
            refresh(showLoading = true)
            observeStateChanges()
        }
    }

    /** 处理安装任务主按钮点击。 */
    fun onPrimaryClick(appId: String, action: PrimaryAction) {
        viewModelScope.launch {
            primaryActionExecutor.execute(appId = appId, action = action)
            refresh()
        }
    }

    /** 切换安装中心总状态筛选条件。 */
    fun onCycleFilter() {
        selectedFilter = selectedFilter.next()
        viewModelScope.launch { refresh() }
    }

    /** 切换安装中心会话筛选条件。 */
    fun onCycleSessionFilter() {
        selectedSessionFilter = selectedSessionFilter.next()
        viewModelScope.launch { refresh() }
    }

    /** 批量启动所有当前可执行的安装任务。 */
    fun onBatchStartRunnable() {
        viewModelScope.launch {
            appManager.getInstallTasks()
                .filter { selectedFilter.matches(it.overallStatus) }
                .filter { selectedSessionFilter.matches(it.sessionBucket) }
                .filter { it.primaryAction == PrimaryAction.INSTALL || it.primaryAction == PrimaryAction.RETRY_INSTALL }
                .forEach { primaryActionExecutor.execute(it.appId, it.primaryAction, it.packageName) }
            refresh()
        }
    }

    /** 批量重试当前筛选范围内的失败安装任务。 */
    fun onRetryFailed() {
        viewModelScope.launch {
            appManager.getInstallTasks()
                .filter { selectedSessionFilter.matches(it.sessionBucket) }
                .filter { it.primaryAction == PrimaryAction.RETRY_INSTALL }
                .forEach { primaryActionExecutor.execute(it.appId, it.primaryAction, it.packageName) }
            refresh()
        }
    }

    /** 按安装会话维度批量重试可恢复会话。 */
    fun onRetryRetryableSessions() {
        viewModelScope.launch {
            installSessionStore.getRetryableSessions()
                .map { it.appId }
                .distinct()
                .forEach { primaryActionExecutor.execute(it, PrimaryAction.RETRY_INSTALL) }
            refresh()
        }
    }

    /** 清理安装中心失败态和失败会话。 */
    fun onClearFailed() {
        viewModelScope.launch {
            // 先清空失败会话，再把应用级失败态恢复成可继续操作的状态。
            installSessionStore.clearFailedSessions()
            appManager.getInstallTasks()
                .filter { it.reasonText != null }
                .forEach { installManager.clearFailed(it.appId) }
            refresh()
        }
    }


    /** 安装中心摘要统计模型。 */
    private data class InstallCenterSummary(
        /** 当前所有失败任务数量。 */
        val failedCount: Int,
        /** 当前可执行安装任务数量。 */
        val runnableCount: Int,
        /** 当前恢复中断会话数量。 */
        val recoveredSessionCount: Int,
        /** 当前可重试会话数量。 */
        val retryableSessionCount: Int,
    )

    /** 计算安装中心顶部控制区需要使用的摘要数据。 */
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

    /** 监听全局安装状态变化。 */
    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /** 重新计算安装中心页面状态。 */
    private suspend fun refresh(showLoading: Boolean = false) {
        if (showLoading) {
            _uiState.value = _uiState.value.copy(screenState = InstallCenterScreenState.Loading)
        }
        runCatching {
            val allTasks = appManager.getInstallTasks()
            val visible = allTasks
                .filter { selectedFilter.matches(it.overallStatus) }
                .filter { selectedSessionFilter.matches(it.sessionBucket) }

            // 基于全部任务和当前可见任务分别计算失败数、可执行数和会话分桶摘要。
            val summary = summarize(allTasks, visible)

            InstallCenterUiState(
                tasks = visible,
                allTaskCount = allTasks.size,
                failedCount = summary.failedCount,
                stats = appManager.getInstallTaskStats(),
                selectedFilter = selectedFilter,
                selectedSessionFilter = selectedSessionFilter,
                batchRunnableCount = summary.runnableCount,
                clearFailedCount = summary.failedCount,
                activeSessionCount = visible.count { it.sessionBucket == SessionBucket.ACTIVE },
                failedSessionCount = visible.count { it.sessionBucket == SessionBucket.FAILED },
                recoveredSessionCount = visible.count { it.sessionBucket == SessionBucket.RECOVERED },
                showFailurePanel = summary.failedCount > 0,
                controlsUiState = InstallCenterControlsUiState(
                    runnableCount = summary.runnableCount,
                    failedCount = summary.failedCount + summary.recoveredSessionCount,
                    retryableSessionCount = summary.retryableSessionCount,
                    recoveredSessionCount = summary.recoveredSessionCount,
                ),
                screenState = if (visible.isEmpty()) {
                    InstallCenterScreenState.Empty
                } else {
                    InstallCenterScreenState.Content
                },
            )
        }.onSuccess { _uiState.value = it }
            .onFailure { throwable ->
                _uiState.value = InstallCenterUiState(
                    selectedFilter = selectedFilter,
                    selectedSessionFilter = selectedSessionFilter,
                    screenState = InstallCenterScreenState.Error(throwable.message.orEmpty()),
                )
            }
    }
}
