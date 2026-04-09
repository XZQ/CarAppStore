package com.nio.appstore.data.model

enum class InstallSessionFilter(
    val label: String,
) {
    ALL("Session-全部"),
    ACTIVE("Session-进行中"),
    FAILED("Session-失败"),
    RECOVERED("Session-中断恢复"),
    COMPLETED("Session-已完成");

    fun next(): InstallSessionFilter {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    fun matches(bucket: SessionBucket): Boolean {
        return when (this) {
            ALL -> true
            ACTIVE -> bucket == SessionBucket.ACTIVE
            FAILED -> bucket == SessionBucket.FAILED
            RECOVERED -> bucket == SessionBucket.RECOVERED
            COMPLETED -> bucket == SessionBucket.COMPLETED
        }
    }
}
