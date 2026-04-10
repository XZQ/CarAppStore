package com.nio.appstore.core.installer

import kotlinx.coroutines.delay

/**
 * SimulatedPackageInstaller 通过延时和虚拟进度模拟安装流程。
 */
class SimulatedPackageInstaller : PackageInstaller {
    /** 执行一次模拟安装。 */
    override suspend fun install(
        request: InstallRequest,
        onEvent: suspend (InstallEvent) -> Unit,
    ) {
        onEvent(InstallEvent.Waiting)
        delay(180L)

        // 先模拟安装前对 APK 文件存在性与有效性的校验。
        if (!request.apkFile.exists()) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_MISSING, InstallFailureCode.APK_MISSING.displayText))
            return
        }
        if (request.apkFile.length() <= 0L) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_INVALID, InstallFailureCode.APK_INVALID.displayText))
            return
        }

        // 校验通过后，依次模拟会话创建、安装执行和进度推进。
        val fakeSessionId = (System.currentTimeMillis() % 100000).toInt()
        onEvent(InstallEvent.SessionCreated(fakeSessionId))
        onEvent(InstallEvent.Installing)
        delay(220L)
        onEvent(InstallEvent.Progress(fakeSessionId, 35))
        delay(220L)
        onEvent(InstallEvent.Progress(fakeSessionId, 70))
        delay(220L)
        onEvent(InstallEvent.Progress(fakeSessionId, 100))
        onEvent(InstallEvent.Success(request.targetVersion))
    }
}
