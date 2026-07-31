package com.xzq.appstore.data.model

/**
 * AppPlatform 描述目录可声明的独立软件平台。
 *
 * 手机、平板、大屏和可选车载形态属于设备形态，不在这里重复建模；
 * 当前 Android 客户端在这些形态上共享同一个 ANDROID 安装包主线。
 */
enum class AppPlatform(
    val wireValue: String,
    val displayName: String,
) {
    ANDROID("android", "Android"),
    IOS("ios", "iOS"),
    MACOS("macos", "macOS"),
    WINDOWS("windows", "Windows"),
    LINUX("linux", "Linux"),
    WEB("web", "Web"),
    ;

    companion object {
        /** 把服务端平台标识转换成已知平台；未知值返回 null 并按不支持处理。 */
        fun fromWireValue(raw: String): AppPlatform? {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.wireValue == normalized }
        }
    }
}

/**
 * ClientPlatformCapabilities 描述当前客户端能够直接安装的软件平台。
 *
 * 当前仓库只交付 Android 客户端，因此默认平台固定为 ANDROID；未来其他客户端
 * 复用业务层时应在装配入口显式注入对应平台。
 */
data class ClientPlatformCapabilities(
    val currentPlatform: AppPlatform = AppPlatform.ANDROID,
) {
    /** 当前客户端能否直接处理应用声明的安装包。 */
    fun supports(supportedPlatforms: Set<AppPlatform>): Boolean = currentPlatform in supportedPlatforms
}
