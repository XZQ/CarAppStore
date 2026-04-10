package com.nio.appstore.core.tracker

import android.util.Log

open class EventTracker {
    /** 记录一次业务事件。 */
    open fun track(event: String) {
        Log.d("EventTracker", event)
    }
}
