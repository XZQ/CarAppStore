package com.nio.appstore.core.installer

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * InstallUserActionDispatcher 用于把系统安装确认动作从 core 层分发给 app 壳层。
 */
class InstallUserActionDispatcher {

    private val _actions = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    /** 壳层监听的系统安装确认动作流。 */
    val actions: SharedFlow<Intent> = _actions.asSharedFlow()

    fun dispatch(intent: Intent) {
        _actions.tryEmit(intent)
    }
}
