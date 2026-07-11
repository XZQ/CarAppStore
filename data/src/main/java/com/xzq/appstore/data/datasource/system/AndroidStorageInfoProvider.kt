package com.xzq.appstore.data.datasource.system

import android.content.Context
import com.xzq.appstore.core.policy.StorageInfoProvider
import java.io.File

/** 基于 Android Context 读取 filesDir 可用空间的实现。 */
class AndroidStorageInfoProvider(context: Context) : StorageInfoProvider {
    private val appContext = context.applicationContext

    override fun usableSpaceBytes(): Long {
        val dir: File = appContext.filesDir
        if (!dir.exists()) dir.mkdirs()
        return dir.usableSpace
    }
}
