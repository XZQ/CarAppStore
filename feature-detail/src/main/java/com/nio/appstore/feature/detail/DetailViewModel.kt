package com.nio.appstore.feature.detail

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.common.ui.CarUiStyle
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.download.DownloadManager
import com.nio.appstore.domain.install.InstallManager
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.StateCenter
import com.nio.appstore.domain.upgrade.UpgradeManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DetailViewModel(
    private val appManager: AppManager,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val upgradeManager: UpgradeManager,
    private val stateCenter: StateCenter,
) : BaseViewModel<DetailUiState>(DetailUiState()) {

    private lateinit var currentAppId: String

    fun load(appId: String) {
        currentAppId = appId
        viewModelScope.launch {
            val detail = appManager.getAppDetail(appId)
            _uiState.value = _uiState.value.copy(appDetail = detail)
            upgradeManager.checkUpgrade(appId)
        }
        stateCenter.observe(appId)
            .onEach { appState ->
                _uiState.value = _uiState.value.copy(
                    stateText = appState.statusText,
                    statusTone = CarUiStyle.resolveStatusTone(appState),
                    primaryAction = appState.primaryAction,
                    progress = appState.progress,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onPrimaryClick() {
        when (_uiState.value.primaryAction) {
            PrimaryAction.DOWNLOAD, PrimaryAction.RETRY_DOWNLOAD -> viewModelScope.launch {
                downloadManager.startDownload(currentAppId)
            }
            PrimaryAction.PAUSE -> viewModelScope.launch {
                downloadManager.pauseDownload(currentAppId)
            }
            PrimaryAction.RESUME -> viewModelScope.launch {
                downloadManager.resumeDownload(currentAppId)
            }
            PrimaryAction.INSTALL, PrimaryAction.RETRY_INSTALL -> viewModelScope.launch {
                installManager.install(currentAppId)
                upgradeManager.checkUpgrade(currentAppId)
            }
            PrimaryAction.OPEN -> _uiState.value.appDetail?.let { appManager.openApp(it.packageName) }
            PrimaryAction.UPGRADE -> viewModelScope.launch {
                upgradeManager.startUpgrade(currentAppId)
            }
            PrimaryAction.DISABLED -> Unit
        }
    }
}
