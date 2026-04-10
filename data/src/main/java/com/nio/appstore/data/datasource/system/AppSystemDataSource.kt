package com.nio.appstore.data.datasource.system

import android.content.Context

class AppSystemDataSource(
    /** 应用级上下文，后续真实实现会用于查询系统包信息。 */
    private val context: Context,
) {
    /** 请求系统打开指定包名应用。 */
    fun openApp(packageName: String): Boolean {
        // 当前仍是演示实现，统一返回成功。
        return true
    }
}
