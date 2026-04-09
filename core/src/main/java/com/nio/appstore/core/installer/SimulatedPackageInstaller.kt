package com.nio.appstore.core.installer

import kotlinx.coroutines.delay

class SimulatedPackageInstaller : PackageInstaller {
    override suspend fun install(
        request: InstallRequest,
        onEvent: suspend (InstallEvent) -> Unit,
    ) {
        onEvent(InstallEvent.Waiting)
        delay(180L)

        if (!request.apkFile.exists()) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_MISSING, InstallFailureCode.APK_MISSING.displayText))
            return
        }
        if (request.apkFile.length() <= 0L) {
            onEvent(InstallEvent.Failed(InstallFailureCode.APK_INVALID, InstallFailureCode.APK_INVALID.displayText))
            return
        }

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
