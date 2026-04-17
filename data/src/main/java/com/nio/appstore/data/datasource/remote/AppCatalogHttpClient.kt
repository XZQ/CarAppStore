package com.nio.appstore.data.datasource.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppCatalogHttpRequest 描述一次远端目录请求。
 */
data class AppCatalogHttpRequest(
    /** 本次请求使用的目录地址。 */
    val endpointUrl: String,
    /** 本次请求附带的自定义请求头。 */
    val headers: Map<String, String> = emptyMap(),
    /** 本地缓存命中的 ETag，用于条件请求。 */
    val eTag: String? = null,
    /** 本地缓存命中的 Last-Modified，用于条件请求。 */
    val lastModified: String? = null,
)

/**
 * AppCatalogHttpResponse 描述远端目录接口返回结果。
 */
data class AppCatalogHttpResponse(
    /** HTTP 状态码。 */
    val statusCode: Int,
    /** 目录原始响应体，304 时允许为空。 */
    val body: String? = null,
    /** 服务端返回的 ETag。 */
    val eTag: String? = null,
    /** 服务端返回的 Last-Modified。 */
    val lastModified: String? = null,
    /** 当前响应是否命中 304。 */
    val notModified: Boolean = false,
)

/**
 * AppCatalogHttpClient 定义远端目录请求客户端。
 */
interface AppCatalogHttpClient {
    /** 请求目录接口并返回响应体与缓存校验信息。 */
    suspend fun fetch(request: AppCatalogHttpRequest): AppCatalogHttpResponse
}

/**
 * HttpUrlConnectionAppCatalogHttpClient 使用 HttpURLConnection 请求目录接口。
 */
class HttpUrlConnectionAppCatalogHttpClient : AppCatalogHttpClient {
    /** 请求目录接口并返回响应体。 */
    override suspend fun fetch(request: AppCatalogHttpRequest): AppCatalogHttpResponse {
        return withContext(Dispatchers.IO) {
            request(request)
        }
    }

    /** 发起 HTTP 请求并返回响应体。 */
    private fun request(request: AppCatalogHttpRequest): AppCatalogHttpResponse {
        val connection = (URL(request.endpointUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            requestMethod = METHOD_GET
            doInput = true
            setRequestProperty(HEADER_ACCEPT, MIME_JSON)
            request.headers.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    setRequestProperty(name, value)
                }
            }
            request.eTag?.takeIf { it.isNotBlank() }?.let { setRequestProperty(HEADER_IF_NONE_MATCH, it) }
            request.lastModified?.takeIf { it.isNotBlank() }?.let { setRequestProperty(HEADER_IF_MODIFIED_SINCE, it) }
        }
        return try {
            val code = connection.responseCode
            if (code == HTTP_NOT_MODIFIED) {
                return AppCatalogHttpResponse(
                    statusCode = code,
                    eTag = connection.getHeaderField(HEADER_ETAG),
                    lastModified = connection.getHeaderField(HEADER_LAST_MODIFIED),
                    notModified = true,
                )
            }
            require(code in SUCCESS_CODE_RANGE) { buildFailureMessage(connection, code) }
            AppCatalogHttpResponse(
                statusCode = code,
                body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                eTag = connection.getHeaderField(HEADER_ETAG),
                lastModified = connection.getHeaderField(HEADER_LAST_MODIFIED),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** 组装非成功状态码的异常文案。 */
    private fun buildFailureMessage(connection: HttpURLConnection, code: Int): String {
        val errorText = runCatching {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
        return if (errorText.isNullOrBlank()) {
            "catalog request failed with code=$code"
        } else {
            "catalog request failed with code=$code message=$errorText"
        }
    }

    private companion object {
        private const val METHOD_GET = "GET"
        private const val MIME_JSON = "application/json"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_IF_NONE_MATCH = "If-None-Match"
        private const val HEADER_IF_MODIFIED_SINCE = "If-Modified-Since"
        private const val HEADER_ETAG = "ETag"
        private const val HEADER_LAST_MODIFIED = "Last-Modified"
        private const val HTTP_NOT_MODIFIED = 304
        private const val CONNECT_TIMEOUT_MILLIS = 1_500
        private const val READ_TIMEOUT_MILLIS = 1_500
        private val SUCCESS_CODE_RANGE = 200..299
    }
}
