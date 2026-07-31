package com.xzq.appstore.data.datasource.system

import android.content.Context
import android.os.storage.StorageManager
import com.xzq.appstore.core.policy.StorageInfoProvider
import java.io.File

/** 基于 Android Context 读取 filesDir 可用空间的实现。 */
class AndroidStorageInfoProvider(context: Context) : StorageInfoProvider {
    private val appContext = context.applicationContext

    override fun usableSpaceBytes(): Long {
        val dir: File = appContext.filesDir
        if (!dir.exists()) dir.mkdirs()
        return runCatching {
            val storageManager = appContext.getSystemService(StorageManager::class.java)
            storageManager.getAllocatableBytes(storageManager.getUuidForPath(dir))
        }.getOrDefault(dir.usableSpace)
    }
}
