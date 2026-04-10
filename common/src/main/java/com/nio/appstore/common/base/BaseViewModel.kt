package com.nio.appstore.common.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BaseViewModel 为页面 ViewModel 提供统一的 StateFlow 状态容器。
 */
abstract class BaseViewModel<T>(initialState: T) : ViewModel() {
    /** 可变的内部 UI 状态流，仅供子类更新。 */
    protected val _uiState = MutableStateFlow(initialState)
    /** 对页面暴露的只读 UI 状态流。 */
    val uiState: StateFlow<T> = _uiState.asStateFlow()
}
