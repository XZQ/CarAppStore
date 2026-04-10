package com.nio.appstore.core.tracker

import android.util.Log

class EventTracker {
    /** 记录一次业务事件。 */
    fun track(event: String) {
        Log.d("EventTracker", event)
    }
}
