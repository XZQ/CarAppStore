package com.xzq.appstore.domain.install

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** 安装会话持有 APK 期间，下载命令和缓存清理跳过该应用。由容器共享。 */
class ApkArtifactAccess {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun tryUse(appId: String, action: suspend () -> Unit): Boolean {
        val lock = locks.computeIfAbsent(appId) { Mutex() }
        if (!lock.tryLock()) return false
        try { action() } finally { lock.unlock() }
        return true
    }
}
