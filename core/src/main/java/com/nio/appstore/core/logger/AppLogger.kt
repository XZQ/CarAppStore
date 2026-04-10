package com.nio.appstore.core.logger

import android.util.Log

open class AppLogger {
    /** 输出调试级日志。 */
    open fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}
