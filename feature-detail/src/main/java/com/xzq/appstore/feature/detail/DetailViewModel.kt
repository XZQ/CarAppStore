package com.xzq.appstore.feature.detail

import androidx.lifecycle.viewModelScope
import com.xzq.appstore.common.base.BaseViewModel
import com.xzq.appstore.common.ui.CarUiStyle
import com.xzq.appstore.core.tracker.EventTracker
import com.xzq.appstore.domain.action.AppPrimaryActionExecutor
import com.xzq.appstore.domain.appmanager.AppManager
import com.xzq.appstore.domain.download.DownloadManager
import com.xzq.appstore.domain.install.InstallManager
import com.xzq.appstore.domain.platform.resolvePlatformPrimaryAction
import com.xzq.appstore.domain.policy.PolicyCenter
import com.xzq.appstore.domain.state.StateCenter
import com.xzq.appstore.domain.state.PrimaryAction
import com.xzq.appstore.domain.text.BusinessText
import com.xzq.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailViewModel(
    /** 提供详情页应用详情和卡片状态数据。 */
    private val appManager: AppManager,
    /** 下载业务入口。 */
    private val downloadManager: DownloadManager,
    /** 安装业务入口。 */
    private val installManager: InstallManager,
    /** 升级业务入口。 */
    private val upgradeManager: UpgradeManager,
    /** 用于监听当前应用运行态变化。 */
    private val stateCenter: StateCenter,
    /** 用于监听页面策略变化。 */
    private val policyCenter: PolicyCenter,
    private val eventTracker: EventTracker = EventTracker(),
) : BaseViewModel<DetailUiState>(DetailUiState()) {
    /** 当前详情页正在展示的应用 id。 */
    private lateinit var currentAppId: String

    /** 详情页应用运行态订阅任务，重复 load 时先取消以避免累积 collector。 */
    private var observeStateJob: Job? = null

    /** 详情页策略订阅任务。 */
    private var observePolicyJob: Job? = null

    /** 详情页与卡片共用的主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
        tracker = eventTracker,
    )

    /** 加载指定应用的详情页数据，并订阅其运行态。重复调用会先取消上一次订阅，避免 collector 累积。 */
    fun load(appId: String) {
        currentAppId = appId
        observeStateJob?.cancel()
        observePolicyJob?.cancel()
        observeStateJob = stateCenter.observe(appId).onEach { appState ->
            // 页面只消费已经归一化的状态文本、主按钮和进度，不自己做业务判断。
            _uiState.update {
                val primaryAction = resolvePlatformPrimaryAction(
                    action = appState.primaryAction,
                    currentPlatformSupported = it.appDetail?.currentPlatformSupported ?: true,
                )
                it.copy(
                    stateText = if (primaryAction == PrimaryAction.UNSUPPORTED) BusinessText.STATUS_PLATFORM_UNSUPPORTED else appState.statusText,
                    statusTone = CarUiStyle.resolveStatusTone(appState),
                    primaryAction = primaryAction,
                    progress = appState.progress,
                )
            }
        }.launchIn(viewModelScope)
        observePolicyJob = policyCenter.observeSettings().onEach {
            if (::currentAppId.isInitialized) {
                _uiState.update {
                    it.copy(policyPrompt = appManager.getPolicyPrompt(), interceptReason = computeInterceptReason(currentAppId))
                }
            }
        }.launchIn(viewModelScope)
        viewModelScope.launch { loadDetail(appId) }
    }

    /** 处理详情页主按钮点击。 */
    fun onPrimaryClick() {
        viewModelScope.launch {
            primaryActionExecutor.execute(appId = currentAppId, action = _uiState.value.primaryAction, packageName = _uiState.value.appDetail?.packageName)
        }
    }

    /** 加载详情页数据并同步升级可用性。 */
    private suspend fun loadDetail(appId: String) {
        _uiState.update { it.copy(screenState = DetailScreenState.Loading) }
        runCatching {
            val detail = appManager.getAppDetail(appId)
            upgradeManager.checkUpgrade(appId)
            detail
        }.onSuccess { detail ->
            _uiState.update {
                val primaryAction = resolvePlatformPrimaryAction(
                    action = stateCenter.snapshot(appId).primaryAction,
                    currentPlatformSupported = detail.currentPlatformSupported,
                )
                it.copy(
                    appDetail = detail,
                    screenState = DetailScreenState.Content,
                    stateText = if (primaryAction == PrimaryAction.UNSUPPORTED) BusinessText.STATUS_PLATFORM_UNSUPPORTED else it.stateText,
                    primaryAction = primaryAction,
                    policyPrompt = appManager.getPolicyPrompt(),
                    interceptReason = computeInterceptReason(appId, detail.currentPlatformSupported),
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(appDetail = null, screenState = DetailScreenState.Error(throwable.message.orEmpty()), policyPrompt = "", interceptReason = "")
            }
        }
    }

    /** 计算当前应用是否被策略拦截下载，返回拦截原因文案（未拦截时为空）。 */
    private fun computeInterceptReason(
        appId: String,
        currentPlatformSupported: Boolean = _uiState.value.appDetail?.currentPlatformSupported ?: true,
    ): String {
        if (!currentPlatformSupported) {
            return BusinessText.STATUS_PLATFORM_UNSUPPORTED
        }
        return policyCenter.canDownload(appId).let { result -> if (!result.allow) result.reason else "" }
    }
}
