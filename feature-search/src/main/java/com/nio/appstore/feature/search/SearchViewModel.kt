package com.nio.appstore.feature.search

import androidx.lifecycle.viewModelScope
import com.nio.appstore.common.base.BaseViewModel
import com.nio.appstore.domain.appmanager.AppManager
import com.nio.appstore.domain.state.StateCenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SearchViewModel(private val appManager: AppManager, private val stateCenter: StateCenter) :
    BaseViewModel<SearchUiState>(SearchUiState()) {

    private var observeJob: Job? = null

    fun load() {
        viewModelScope.launch {
            refresh(_uiState.value.keyword)
            observeStateChanges()
        }
    }

    fun search(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
        viewModelScope.launch { refresh(keyword) }
    }

    private fun observeStateChanges() {
        if (observeJob != null) return
        observeJob = stateCenter.observeAll()
            .onEach { refresh(_uiState.value.keyword) }
            .launchIn(viewModelScope)
    }

    private suspend fun refresh(keyword: String) {
        _uiState.value = _uiState.value.copy(
            apps = appManager.searchApps(keyword),
            policyPrompt = appManager.getPolicyPrompt(),
        )
    }
}
