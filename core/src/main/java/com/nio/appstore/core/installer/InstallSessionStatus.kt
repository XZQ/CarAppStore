package com.nio.appstore.core.installer

object InstallSessionStatus {
    const val CREATED = "CREATED"
    const val WRITTEN = "WRITTEN"
    const val COMMITTED = "COMMITTED"
    const val CALLBACK_SUCCESS = "CALLBACK_SUCCESS"
    const val RECOVERED_INTERRUPTED = "RECOVERED_INTERRUPTED"

    const val FAILED_CREATE = "FAILED_CREATE"
    const val FAILED_WRITE = "FAILED_WRITE"
    const val FAILED_COMMIT = "FAILED_COMMIT"

    fun isRecoverable(status: String): Boolean {
        return status == CREATED || status == WRITTEN || status == COMMITTED
    }

    fun isFailed(status: String): Boolean {
        return status.startsWith("FAILED_")
    }

    fun isRetryable(status: String): Boolean {
        return status == RECOVERED_INTERRUPTED || isFailed(status)
    }
}
