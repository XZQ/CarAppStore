package com.xzq.appstore.data.datasource.remote

import android.util.AtomicFile
import com.xzq.appstore.core.logger.AppLogger
import java.io.File
import java.util.UUID

/** 仅在本机 noBackup 目录保存随机灰度分组，不读取设备身份，也不跨安装恢复。调用方在 IO 线程读取。 */
internal class CatalogInstallationIdStore(noBackupDirectory: File) {
    private val file = AtomicFile(File(noBackupDirectory, "catalog_installation_id"))

    fun getOrCreate(): String = synchronized(lock) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            val stored = file.readFully().toString(Charsets.UTF_8).trim()
            try {
                return@synchronized UUID.fromString(stored).toString()
            } catch (invalid: IllegalArgumentException) {
                AppLogger().w("CatalogCohort", "Invalid local rollout cohort; generating a new one", invalid)
            }
        }
        val id = UUID.randomUUID().toString()
        val output = file.startWrite()
        try {
            output.write(id.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
        id
    }

    private companion object {
        val lock = Any()
    }
}
