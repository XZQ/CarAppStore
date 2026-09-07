package com.xzq.appstore.core.policy

/** 空间预检的保守预算；系统安装器仍可能因解压、其他进程写入而报告空间不足。 */
object StorageBudget {
    const val RESERVE_BYTES = 32L * 1024 * 1024
    const val CHECK_INTERVAL_BYTES = 1024L * 1024

    fun fits(available: Long, vararg allocations: Long): Boolean {
        var remaining = available
        for (bytes in longArrayOf(RESERVE_BYTES, *allocations)) {
            if (bytes < 0 || remaining < bytes) return false
            remaining -= bytes
        }
        return true
    }

    fun canDownload(available: Long, total: Long, downloaded: Long): Boolean = if (total > 0) {
        fits(available, total - downloaded.coerceIn(0, total), total)
    } else {
        fits(available, downloaded.coerceAtLeast(0), CHECK_INTERVAL_BYTES)
    }

    fun canInstall(available: Long, apkBytes: Long): Boolean = fits(available, apkBytes, apkBytes)
}
