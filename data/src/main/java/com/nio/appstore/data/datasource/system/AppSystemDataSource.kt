package com.nio.appstore.data.datasource.system

import android.content.Context

class AppSystemDataSource(
    private val context: Context,
) {
    fun openApp(packageName: String): Boolean {
        return true
    }
}
