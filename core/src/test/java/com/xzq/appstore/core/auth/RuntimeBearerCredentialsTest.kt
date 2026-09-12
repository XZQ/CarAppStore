package com.xzq.appstore.core.auth

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class RuntimeBearerCredentialsTest {
    @Test
    fun `credentials rotate for each request and never appear in diagnostics`() {
        val credentials = RuntimeBearerCredentials("https://catalog.test/api", clockMillis = { 1000 })
        credentials.authorize("test-only-first", 121000)
        assertEquals("Bearer test-only-first", credentials.headersFor("https://CATALOG.test:443/other")["Authorization"])
        credentials.authorize("test-only-second", 121000)
        assertEquals("Bearer test-only-second", credentials.headersFor("https://catalog.test/api")["Authorization"])
        assertFalse(credentials.toString().contains("test-only"))
    }

    @Test(expected = AuthenticationRequiredException::class)
    fun `required authentication cannot silently fall back to anonymous`() {
        RuntimeBearerCredentials("https://catalog.test").headersFor("https://catalog.test/api")
    }

    @Test(expected = AuthenticationRequiredException::class)
    fun `clearing session prevents the next authenticated request`() {
        val credentials = RuntimeBearerCredentials("https://catalog.test", clockMillis = { 1000 })
        credentials.authorize("test-only", 121000)
        credentials.clear()
        credentials.headersFor("https://catalog.test")
    }

    @Test(expected = IOException::class)
    fun `credentials cannot leave their configured origin`() {
        val credentials = RuntimeBearerCredentials("https://catalog.test", clockMillis = { 1000 })
        credentials.authorize("test-only", 121000)
        credentials.headersFor("https://untrusted.test/api")
    }

    @Test(expected = AuthenticationRequiredException::class)
    fun `clock rollback does not extend credential lifetime`() {
        var wall = 1000L
        var monotonic = 0L
        val credentials = RuntimeBearerCredentials("https://catalog.test", { wall }, { monotonic })
        credentials.authorize("test-only", 121000)
        wall = 0
        monotonic = 90_000_000_000L
        credentials.headersFor("https://catalog.test")
    }

    @Test
    fun `rejects long lived tokens header injection and cleartext endpoints`() {
        val credentials = RuntimeBearerCredentials("https://catalog.test", clockMillis = { 1000 })
        assertThrows(IllegalArgumentException::class.java) { credentials.authorize("test-only", 1_000_000) }
        assertThrows(IllegalArgumentException::class.java) { credentials.authorize("test\r\nInjected: value", 121000) }
        assertThrows(IllegalArgumentException::class.java) { RuntimeBearerCredentials("http://catalog.test") }
        assertTrue(RuntimeBearerCredentials(null).headersFor("https://public.test").isEmpty())
    }
}
