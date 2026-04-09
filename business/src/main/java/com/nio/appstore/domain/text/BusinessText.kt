package com.nio.appstore.domain.text

/**
 * BusinessText 用于收敛业务层当前直接写在代码中的状态文案和策略提示文案。
 *
 * 这里先作为 M8 阶段的统一文案入口：
 * 1. 避免 reducer / manager / policy 中重复散落字符串；
 * 2. 不让业务层直接依赖 Android 资源；
 * 3. 为后续继续做文案策略收口和国际化迁移保留统一替换点。
 */
object BusinessText {
    const val STATUS_UPGRADING = "升级中"
    const val STATUS_UPGRADE_FAILED = "升级失败"
    const val STATUS_INSTALLING = "安装中"
    const val STATUS_WAITING_INSTALL = "等待安装"
    const val STATUS_INSTALL_FAILED = "安装失败"
    const val STATUS_UPGRADE_AVAILABLE = "可升级"
    const val STATUS_INSTALLED = "已安装"
    const val STATUS_WAITING_DOWNLOAD = "等待下载"
    const val STATUS_DOWNLOAD_COMPLETED = "下载完成"
    const val STATUS_DOWNLOAD_FAILED = "下载失败"
    const val STATUS_CANCELED = "已取消"
    const val STATUS_NOT_INSTALLED = "未安装"

    const val POLICY_NOT_WIFI = "当前非 Wi‑Fi 网络"
    const val POLICY_LOW_STORAGE = "当前存储空间不足"
    const val POLICY_DEVICE_STORAGE_LOW = "设备可用存储不足"
    const val POLICY_NOT_PARKING = "当前非驻车状态"

    const val DOWNLOAD_RECOVERED_SEGMENTS = "检测到临时分片，可继续下载"
    const val DOWNLOAD_APK_MISSING = "安装包已丢失，请重新下载"
    const val DOWNLOAD_FILE_INCOMPLETE = "文件未完整下载，可继续下载"
    const val DOWNLOAD_RECOVERED_PROGRESS = "已恢复临时下载进度，可继续下载"
    const val DOWNLOAD_FILE_MISSING = "下载文件已丢失，请重新下载"

    fun downloading(progress: Int): String = "下载中 $progress%"
    fun paused(progress: Int): String = "已暂停 $progress%"
}
