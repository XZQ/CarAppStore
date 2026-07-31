package com.xzq.appstore.domain.platform

import com.xzq.appstore.domain.state.PrimaryAction

/**
 * 把通用任务状态动作与当前客户端的平台能力合并。
 *
 * 已安装应用仍允许打开，进行中的下载仍允许暂停；其他会产生新安装包动作的入口
 * 在当前平台不受支持时统一变成不可点击的 UNSUPPORTED。
 */
fun resolvePlatformPrimaryAction(
    action: PrimaryAction,
    currentPlatformSupported: Boolean,
): PrimaryAction {
    if (currentPlatformSupported) {
        return action
    }
    return when (action) {
        PrimaryAction.OPEN,
        PrimaryAction.PAUSE,
        PrimaryAction.DISABLED,
            -> action

        else -> PrimaryAction.UNSUPPORTED
    }
}
