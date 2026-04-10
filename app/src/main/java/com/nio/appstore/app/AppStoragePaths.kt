package com.nio.appstore.app

import android.content.Context
import java.io.File

/**
 * AppStoragePaths 负责集中管理 app 壳层当前会用到的本地文件路径。
 *
 * 这样可以避免这些路径常量分散在 AppContainer 中，后续如果继续把装配逻辑
 * 向更独立的 assembly / bootstrap 结构收敛，也能更容易迁移。
 */
class AppStoragePaths(context: Context) {
    /** 统一提供文件目录时使用的应用级上下文。 */
    private val appContext = context.applicationContext

    /** 统一数据层当前使用的结构化 JSON 落盘文件。 */
    val structuredLocalStoreFile: File =
        appContext.filesDir.resolve("structured_local_store.json")

    /** 安装会话兜底使用的 JSON 文件。 */
    val installSessionsFile: File =
        appContext.filesDir.resolve("install_sessions.json")

    /** 下载器工作目录。 */
    val downloadsDir: File =
        appContext.filesDir.resolve("downloads")
}
