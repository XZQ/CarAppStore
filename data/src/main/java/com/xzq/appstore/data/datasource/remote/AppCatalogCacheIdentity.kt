package com.xzq.appstore.data.datasource.remote

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale

/** 绑定目录缓存的响应维度和正文，运行时认证头由 HTTP 客户端追加，不参与此摘要。 */
internal object AppCatalogCacheIdentity {
    /** 同一地址、渠道、平台和灰度配置才允许复用缓存及条件请求校验值。 */
    fun requestFingerprint(request: AppCatalogHttpRequest): String {
        val fields = buildList {
            add(request.endpointUrl)
            request.headers.entries.sortedWith(compareBy({ it.key.lowercase(Locale.ROOT) }, { it.value }))
                .forEach { (name, value) ->
                    add(name.lowercase(Locale.ROOT))
                    add(value)
                }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().toHex()
    }

    /** 正文完整性校验不依赖远端 ETag 的实现方式。 */
    fun bodySha256(body: String): String = MessageDigest.getInstance("SHA-256")
        .digest(body.toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
