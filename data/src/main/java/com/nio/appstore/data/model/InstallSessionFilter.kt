package com.nio.appstore.data.model

enum class InstallSessionFilter(
    /** 展示给用户的会话筛选标签。 */
    val label: String,
) {
    ALL(ModelText.SESSION_FILTER_ALL),
    ACTIVE(ModelText.SESSION_FILTER_ACTIVE),
    FAILED(ModelText.SESSION_FILTER_FAILED),
    RECOVERED(ModelText.SESSION_FILTER_RECOVERED),
    COMPLETED(ModelText.SESSION_FILTER_COMPLETED);

    /** 获取下一个会话筛选项。 */
    fun next(): InstallSessionFilter {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    /** 判断当前筛选项是否匹配目标会话分组。 */
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
