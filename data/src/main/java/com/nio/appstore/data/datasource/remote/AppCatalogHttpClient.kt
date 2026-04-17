package com.nio.appstore.data.datasource.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppCatalogHttpClient 定义远端目录请求客户端。
 */
interface AppCatalogHttpClient {
    /** 请求目录接口并返回原始响应体。 */
    suspend fun fetch(endpointUrl: String): String
}

/**
 * HttpUrlConnectionAppCatalogHttpClient 使用 HttpURLConnection 请求目录接口。
 */
class HttpUrlConnectionAppCatalogHttpClient : AppCatalogHttpClient {
    /** 请求目录接口并返回响应体。 */
    override suspend fun fetch(endpointUrl: String): String {
        return withContext(Dispatchers.IO) {
            request(endpointUrl)
        }
    }

    /** 发起 HTTP 请求并返回响应体。 */
    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            requestMethod = METHOD_GET
            doInput = true
        }
        return try {
            val code = connection.responseCode
            require(code in SUCCESS_CODE_RANGE) { "catalog request failed with code=$code" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        private const val METHOD_GET = "GET"
        private const val CONNECT_TIMEOUT_MILLIS = 1_500
        private const val READ_TIMEOUT_MILLIS = 1_500
        private val SUCCESS_CODE_RANGE = 200..299
    }
}
