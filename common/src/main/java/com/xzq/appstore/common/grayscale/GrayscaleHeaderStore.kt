package com.xzq.appstore.common.grayscale

import android.content.Context
import androidx.core.content.edit
import com.xzq.appstore.common.grayscale.GrayscaleHeaderStore.HEADER_NAME

/**
 * GrayscaleHeaderStore 统一保存与读取开发者调试用的灰度分组标识。
 *
 * 开发者设置页（feature-debug）写入，目录请求链路（data）读取并以固定请求头下发，
 * 两者通过同名的 SharedPreferences 契约解耦，避免 data 反向依赖 feature-debug。
 *
 * 约定：灰度开关关闭或标识为空时不注入任何请求头；启用时以下发 [HEADER_NAME]
 * 作为灰度分组标识，供后端按 rollout 过滤可见应用。
 */
object GrayscaleHeaderStore {
    /** 共享的 SharedPreferences 文件名。 */
    private const val PREFS_NAME = "car_app_store_grayscale"

    /** 灰度开关键。 */
    private const val KEY_ENABLED = "grayscale_enabled"

    /** 灰度分组标识键。 */
    private const val KEY_HEADER = "grayscale_header"

    /** 灰度标识下发的请求头名称。 */
    const val HEADER_NAME = "X-Grayscale-Tag"

    /** 当前保存的灰度头配置。 */
    data class GrayscaleHeaderConfig(
        /** 是否启用灰度头。 */
        val enabled: Boolean,
        /** 灰度分组标识值，作为 [HEADER_NAME] 的值下发。 */
        val tag: String,
    )

    /** 读取当前灰度头配置，未设置时返回关闭态。 */
    fun read(context: Context): GrayscaleHeaderConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GrayscaleHeaderConfig(enabled = prefs.getBoolean(KEY_ENABLED, false), tag = prefs.getString(KEY_HEADER, "").orEmpty())
    }

    /** 持久化灰度头配置。 */
    fun save(context: Context, enabled: Boolean, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
            putString(KEY_HEADER, tag)
        }
    }

    /** 把配置转换为请求头键值对；未启用或标识为空时返回 null。 */
    fun toHeader(config: GrayscaleHeaderConfig): Pair<String, String>? {
        if (!config.enabled || config.tag.isBlank()) {
            return null
        }
        return HEADER_NAME to config.tag
    }
}
