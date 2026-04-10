package com.nio.appstore.core.logger

import android.util.Log

class AppLogger {
    /** 输出调试级日志。 */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}
