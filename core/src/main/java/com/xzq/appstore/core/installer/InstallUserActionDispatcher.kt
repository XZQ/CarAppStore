package com.xzq.appstore.core.installer

import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * InstallUserActionDispatcher 用于把系统安装确认动作从 core 层分发给 app 壳层。
 */
class InstallUserActionDispatcher(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    data class PendingAction(val sessionId: Int, val intent: Intent, val expiresAt: Long)

    private val _actions = MutableStateFlow<Map<Int, PendingAction>>(emptyMap())
    private val deliveredSessions = mutableSetOf<Int>()

    /** 页面停止监听时仍保留待处理会话，恢复监听即可继续。 */
    val actions: StateFlow<Map<Int, PendingAction>> = _actions.asStateFlow()

    @Synchronized
    fun dispatch(sessionId: Int, intent: Intent, lifetimeMs: Long = 300_000L) {
        if (sessionId in deliveredSessions || sessionId in _actions.value) return
        _actions.value = _actions.value + (sessionId to PendingAction(sessionId, intent, elapsedRealtime() + lifetimeMs))
    }

    /** 启动成功才消费；启动抛错时保留动作，供下次回到前台重试。 */
    @Synchronized
    fun launchPending(sessionId: Int, launch: (Intent) -> Unit): Boolean {
        val action = _actions.value[sessionId] ?: return false
        if (elapsedRealtime() >= action.expiresAt) {
            clear(sessionId)
            return false
        }
        launch(action.intent)
        deliveredSessions += sessionId
        _actions.value = _actions.value - sessionId
        return true
    }

    /** 最终回调、失败或超时后使该会话所有动作失效。 */
    @Synchronized
    fun clear(sessionId: Int) {
        _actions.value = _actions.value - sessionId
        deliveredSessions -= sessionId
    }
}
