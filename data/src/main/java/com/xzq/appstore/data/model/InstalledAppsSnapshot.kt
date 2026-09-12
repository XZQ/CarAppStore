package com.xzq.appstore.data.model

/** 系统安装事实；目录缺项、缓存回退与不可见包不能写入 confirmedAbsentAppIds。 */
data class InstalledAppsSnapshot(
    val apps: List<InstalledApp>,
    val confirmedAbsentAppIds: Set<String> = emptySet(),
)
