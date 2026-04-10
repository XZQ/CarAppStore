package com.nio.appstore.common.result

sealed class AppResult<out T> {
    /** 成功结果，携带有效数据。 */
    data class Success<T>(val data: T) : AppResult<T>()
    /** 失败结果，携带可展示的错误信息。 */
    data class Error(val message: String) : AppResult<Nothing>()
}
