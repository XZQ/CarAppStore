package com.nio.appstore.feature.myapp

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MyAppViewModel(private val appManager: AppManager, private val stateCenter: StateCenter) :
    BaseViewModel<MyAppUiState>(MyAppUiState()) {

    private var observeJob: Job? = null

    fun load() {
        viewModelScope.launch {
            refreshApps()
            observeStateChanges()
        }
    }

    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach {
                refreshApps()
            }
            .launchIn(viewModelScope)
    }

    private suspend fun refreshApps() {
        _uiState.value = MyAppUiState(appManager.getMyApps())
    }
}
