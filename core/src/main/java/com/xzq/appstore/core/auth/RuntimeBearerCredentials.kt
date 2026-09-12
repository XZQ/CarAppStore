package com.xzq.appstore.core.auth

import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/** 短期 Bearer 凭证只驻留内存；每次请求重新读取，禁止向其他 origin 或重定向转发。 */
class RuntimeBearerCredentials(
    endpointUrl: String?,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val monotonicNanos: () -> Long = System::nanoTime,
) {
    private val allowedOrigin = endpointUrl?.let(::httpsOrigin)
    private val credential = AtomicReference<Credential?>(null)

    /** 仅由已认证的登录/会话接入层调用；不得从 BuildConfig 或长期静态密钥生成。 */
    fun authorize(token: String, expiresAtMillis: Long) {
        require(allowedOrigin != null) { "Runtime authentication is not enabled" }
        require(token.length in 1..4096 && TOKEN_PATTERN.matches(token)) { "Invalid bearer credential format" }
        val now = clockMillis()
        require(expiresAtMillis > now && expiresAtMillis - now in (EXPIRY_MARGIN_MS + 1)..MAX_LIFETIME_MS) {
            "Credential lifetime must be between 30 seconds and 15 minutes"
        }
        credential.set(Credential(token, expiresAtMillis, monotonicNanos(),
            (expiresAtMillis - now - EXPIRY_MARGIN_MS) * 1_000_000))
    }

    fun clear() { credential.set(null) }

    fun headersFor(url: String): Map<String, String> {
        val origin = allowedOrigin ?: return emptyMap()
        if (httpsOrigin(url) != origin) throw IOException("Request origin is not authorized")
        val current = credential.get() ?: throw AuthenticationRequiredException()
        if (clockMillis() >= current.expiresAtMillis - EXPIRY_MARGIN_MS ||
            monotonicNanos() - current.receivedAtNanos >= current.validForNanos) {
            credential.compareAndSet(current, null)
            throw AuthenticationRequiredException()
        }
        return mapOf("Authorization" to "Bearer ${current.token}")
    }

    override fun toString(): String = "RuntimeBearerCredentials(redacted)"

    // 不使用 data class，避免自动 toString/copy 暴露 token。
    private class Credential(val token: String, val expiresAtMillis: Long, val receivedAtNanos: Long, val validForNanos: Long)

    private companion object {
        const val MAX_LIFETIME_MS = 15 * 60 * 1000L
        const val EXPIRY_MARGIN_MS = 30_000L
        val TOKEN_PATTERN = Regex("[A-Za-z0-9._~+/-]+=*")

        fun httpsOrigin(url: String): String {
            val uri = try { URI(url) } catch (_: java.net.URISyntaxException) {
                throw IllegalArgumentException("Invalid request URL")
            }
            require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null && uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)) {
                "Runtime credentials require an absolute HTTPS URL without user info or fragment"
            }
            return "${uri.host.lowercase(Locale.ROOT)}:${if (uri.port == -1) 443 else uri.port}"
        }
    }
}

class AuthenticationRequiredException : IOException("短期授权缺失或已过期，请重新认证后重试")
