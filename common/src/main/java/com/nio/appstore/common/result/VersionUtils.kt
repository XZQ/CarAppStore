package com.nio.appstore.common.result

object VersionUtils {
    fun isNewerVersion(current: String?, latest: String?): Boolean {
        if (current.isNullOrBlank() || latest.isNullOrBlank()) return false
        val currentParts = current.split('.').map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxSize) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
