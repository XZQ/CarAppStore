package com.xzq.appstore.data.datasource.system

import android.content.Context
import android.os.storage.StorageManager
import com.xzq.appstore.core.policy.StorageInfoProvider
import java.io.File
import java.io.IOException

/** 统一提供策略预检与下载目录的 Android 可写空间估计。 */
class AndroidStorageInfoProvider(context: Context) : StorageInfoProvider {
    private val appContext = context.applicationContext

    override fun usableSpaceBytes(): Long = usableSpaceBytes(appContext.filesDir)

    /** 未主动申请系统缓存回收，因此估计值不超过目录当前的实际可写空间。 */
    fun usableSpaceBytes(directory: File): Long {
        val writableBytes = writableSpaceBytes(directory)
        if (writableBytes == 0L) return 0L
        val allocatableBytes = try {
            val storageManager = appContext.getSystemService(StorageManager::class.java)
            storageManager?.getAllocatableBytes(storageManager.getUuidForPath(directory)) ?: writableBytes
        } catch (_: IOException) {
            writableBytes
        } catch (_: SecurityException) {
            writableBytes
        }
        return minOf(writableBytes, allocatableBytes.coerceAtLeast(0L))
    }

    /** 下载每 MiB 复查时只读文件系统，避免反复跨进程查询系统可分配量。 */
    fun writableSpaceBytes(directory: File): Long {
        if (!directory.exists() && !directory.mkdirs()) return 0L
        if (!directory.isDirectory) return 0L
        return directory.usableSpace.coerceAtLeast(0L)
    }
}
