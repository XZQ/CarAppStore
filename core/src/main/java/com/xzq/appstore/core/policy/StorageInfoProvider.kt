package com.xzq.appstore.core.policy

/** 提供设备存储空间信息，用于策略中心判断下载/安装前置条件。 */
interface StorageInfoProvider {
    /** 返回当前可用于写入的字节数。 */
    fun usableSpaceBytes(): Long
}

/** 默认实现：始终返回足够空间，适合不关心存储限制的测试或非生产环境。 */
object NoOpStorageInfoProvider : StorageInfoProvider {
    private const val PLENTY_BYTES = Long.MAX_VALUE

    override fun usableSpaceBytes(): Long = PLENTY_BYTES
}
