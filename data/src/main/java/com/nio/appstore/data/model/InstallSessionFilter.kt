package com.nio.appstore.data.model

enum class InstallSessionFilter(
    val label: String,
) {
    ALL(ModelText.SESSION_FILTER_ALL),
    ACTIVE(ModelText.SESSION_FILTER_ACTIVE),
    FAILED(ModelText.SESSION_FILTER_FAILED),
    RECOVERED(ModelText.SESSION_FILTER_RECOVERED),
    COMPLETED(ModelText.SESSION_FILTER_COMPLETED);

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
