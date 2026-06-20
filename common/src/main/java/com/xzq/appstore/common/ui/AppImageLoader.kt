package com.xzq.appstore.common.ui

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppImageLoader {

    private val memoryCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(
        imageView: ImageView,
        source: String,
        fallbackView: TextView? = null,
    ) {
        val normalized = source.trim()
        imageView.tag = normalized
        if (normalized.isBlank()) {
            showFallback(imageView, fallbackView)
            return
        }

        memoryCache.get(normalized)?.let { cached ->
            fallbackView?.visibility = View.GONE
            imageView.setImageBitmap(cached)
            imageView.visibility = View.VISIBLE
            return
        }

        imageView.visibility = View.INVISIBLE
        Thread {
            val bitmap = runCatching { decode(imageView, normalized) }.getOrNull()
            imageView.post {
                if (imageView.tag != normalized) return@post
                if (bitmap != null) {
                    memoryCache.put(normalized, bitmap)
                    fallbackView?.visibility = View.GONE
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                } else {
                    showFallback(imageView, fallbackView)
                }
            }
        }.start()
    }

    private fun decode(imageView: ImageView, source: String) = when {
        source.startsWith("asset://") -> {
            val path = source.removePrefix("asset://")
            imageView.context.assets.open(path).use(BitmapFactory::decodeStream)
        }
        source.startsWith("file://") -> {
            BitmapFactory.decodeFile(Uri.parse(source).path)
        }
        source.startsWith("/") -> {
            BitmapFactory.decodeFile(File(source).absolutePath)
        }
        source.startsWith("http://") || source.startsWith("https://") -> {
            val connection = URL(source).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.inputStream.use(BitmapFactory::decodeStream)
        }
        else -> null
    }

    private fun showFallback(imageView: ImageView, fallbackView: TextView?) {
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        fallbackView?.visibility = View.VISIBLE
    }
}
