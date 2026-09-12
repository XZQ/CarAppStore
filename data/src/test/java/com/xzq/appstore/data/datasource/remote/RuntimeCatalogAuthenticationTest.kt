package com.xzq.appstore.data.datasource.remote

import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RuntimeCatalogAuthenticationTest {
    @Test
    fun `catalog rereads runtime headers and does not log reflected error bodies`() = runBlocking {
        ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val worker = Executors.newSingleThreadExecutor()
            val requests = worker.submit<List<String>> {
                (1..2).map { index ->
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        val body = if (index == 1) "{}" else "must-not-appear-in-diagnostics"
                        val status = if (index == 1) "200 OK" else "401 Unauthorized"
                        socket.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                        headers.first { it.startsWith("X-Test-Runtime:", ignoreCase = true) }.substringAfter(":").trim()
                    }
                }
            }
            try {
                var token = "test-only-first"
                val client = HttpUrlConnectionAppCatalogHttpClient { mapOf("X-Test-Runtime" to token) }
                val request = AppCatalogHttpRequest("http://127.0.0.1:${server.localPort}/catalog")
                assertEquals(200, client.fetch(request).statusCode)
                token = "test-only-second"
                val failure = runCatching { client.fetch(request) }.exceptionOrNull()
                assertNotNull(failure)
                assertTrue(failure?.message.orEmpty().contains("401"))
                assertFalse(failure?.message.orEmpty().contains("must-not-appear"))
                assertEquals(listOf("test-only-first", "test-only-second"), requests.get(5, TimeUnit.SECONDS))
            } finally { worker.shutdownNow() }
        }
    }
}
