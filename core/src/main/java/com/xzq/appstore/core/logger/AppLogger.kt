package com.xzq.appstore.core.logger

import android.util.Log

open class AppLogger {
    /** 输出调试级日志。 */
    open fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    /** 输出警告级日志并保留异常堆栈。 */
    open fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }
}
