package com.xzq.appstore.common.ui

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import coil.load
import coil.size.Scale

/**
 * 应用统一的图片加载入口，基于 Coil 实现：
 * - 自动内存 + 磁盘缓存
 * - 自动按 ImageView 尺寸采样，避免 OOM
 * - 原生支持 asset:// / file:// / http(s)://
 * - 绑定到 ImageView 生命周期，列表回收自动取消
 * - 失败时回退到文本兜底视图
 *
 * 调用方签名保持稳定，业务层无需感知底层库。
 */
object AppImageLoader {
    /**
     * 异步加载图片到 [imageView]。
     *
     * @param source 图片地址，支持 asset:// / file:// / http(s):// / 本地绝对路径
     * @param fallbackView 失败或空地址时显示的兜底视图，通常为首字母 TextView
     */
    fun load(
        imageView: ImageView,
        source: String,
        fallbackView: TextView? = null,
    ) {
        val normalized = source.trim()
        if (normalized.isBlank()) {
            showFallback(imageView, fallbackView)
            return
        }

        imageView.load(normalized) {
            scale(Scale.FILL)
            listener(
                onStart = {
                    imageView.visibility = View.INVISIBLE
                    fallbackView?.visibility = View.GONE
                },
                onSuccess = { _, _ ->
                    imageView.visibility = View.VISIBLE
                    fallbackView?.visibility = View.GONE
                },
                onError = { _, _ ->
                    showFallback(imageView, fallbackView)
                },
            )
        }
    }

    private fun showFallback(
        imageView: ImageView,
        fallbackView: TextView?,
    ) {
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        fallbackView?.visibility = View.VISIBLE
    }
}
