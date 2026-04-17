package com.nio.appstore.feature.detail

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.domain.action.AppPrimaryActionExecutor
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.policy.PolicyCenter
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
) : BaseViewModel<DetailUiState>(DetailUiState()) {

    /** 当前详情页正在展示的应用 id。 */
    private lateinit var currentAppId: String
    /** 策略订阅任务。 */
    private var observePolicyJob: Job? = null

    /** 详情页与卡片共用的主动作分发器。 */
    private val primaryActionExecutor = AppPrimaryActionExecutor(
        appManager = appManager,
        downloadManager = downloadManager,
        installManager = installManager,
        upgradeManager = upgradeManager,
    )

    /** 加载指定应用的详情页数据，并订阅其运行态。 */
    fun load(appId: String) {
        currentAppId = appId
        viewModelScope.launch {
            loadDetail(appId)
        }
        stateCenter.observe(appId)
            .onEach { appState ->
                // 页面只消费已经归一化的状态文本、主按钮和进度，不自己做业务判断。
                _uiState.value = _uiState.value.copy(
                    stateText = appState.statusText,
                    statusTone = CarUiStyle.resolveStatusTone(appState),
                    primaryAction = appState.primaryAction,
                    progress = appState.progress,
                )
            }
            .launchIn(viewModelScope)
        observePolicyChanges()
    }

    /** 处理详情页主按钮点击。 */
    fun onPrimaryClick() {
        viewModelScope.launch {
            primaryActionExecutor.execute(
                appId = currentAppId,
                action = _uiState.value.primaryAction,
                packageName = _uiState.value.appDetail?.packageName,
            )
        }
    }

    /** 加载详情页数据并同步升级可用性。 */
    private suspend fun loadDetail(appId: String) {
        _uiState.value = _uiState.value.copy(screenState = DetailScreenState.Loading)
        runCatching {
            val detail = appManager.getAppDetail(appId)
            upgradeManager.checkUpgrade(appId)
            _uiState.value.copy(
                appDetail = detail,
                screenState = DetailScreenState.Content,
                policyPrompt = appManager.getPolicyPrompt(),
            )
        }.onSuccess { _uiState.value = it }
            .onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    appDetail = null,
                    screenState = DetailScreenState.Error(throwable.message.orEmpty()),
                    policyPrompt = "",
                )
            }
    }

    /** 监听页面策略变化，并在变化时刷新当前提示。 */
    private fun observePolicyChanges() {
        if (observePolicyJob != null) return
        observePolicyJob = policyCenter.observeSettings()
            .onEach {
                if (::currentAppId.isInitialized) {
                    _uiState.value = _uiState.value.copy(policyPrompt = appManager.getPolicyPrompt())
                }
            }
            .launchIn(viewModelScope)
    }
}
