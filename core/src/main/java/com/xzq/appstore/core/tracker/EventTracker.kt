package com.xzq.appstore.core.tracker

import android.util.Log
import java.io.File

open class EventTracker {
    /** 记录一次业务事件。 */
    open fun track(event: String) {
        runCatching { Log.d("EventTracker", event) }
    }
}

class FileEventTracker(
    private val eventLogFile: File,
) : EventTracker() {

    override fun track(event: String) {
        super.track(event)
        val safeEvent = event.replace('\n', ' ').replace('\r', ' ')
        runCatching {
            eventLogFile.parentFile?.mkdirs()
            eventLogFile.appendText("${System.currentTimeMillis()}\t$safeEvent\n", Charsets.UTF_8)
        }.onFailure { error ->
            Log.w("EventTracker", "failed to persist event: ${error.message}")
        }
    }
}
