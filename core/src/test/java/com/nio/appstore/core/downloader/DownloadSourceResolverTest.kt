package com.nio.appstore.core.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DownloadSourceResolverTest 验证下载源解析器在各策略和环境配置下的决策逻辑。
 */
class DownloadSourceResolverTest {

    /** 合法的 HTTP URL。 */
    private val httpUrl = "http://example.com/file.apk"

    /** 合法的 HTTPS URL。 */
    private val httpsUrl = "https://example.com/file.apk"

    /** 不合法的 URL（ftp 协议）。 */
    private val ftpUrl = "ftp://example.com/file.apk"

    /** DIRECT_HTTP 策略 + 合法 http URL → 返回 DIRECT_HTTP 源。 */
    @Test
    fun testResolveDirectHttp_withHttpUrl_returnsDirectHttp() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true)
        )
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, httpUrl)
        assertEquals(DownloadSourcePolicy.DIRECT_HTTP, decision.policy)
        assertEquals(DownloaderText.DIRECT_HTTP_ENABLED, decision.reason)
    }

    /** DIRECT_HTTP 策略 + 合法 https URL → 返回 DIRECT_HTTP 源。 */
    @Test
    fun testResolveDirectHttp_withHttpsUrl_returnsDirectHttp() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true)
        )
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, httpsUrl)
        assertEquals(DownloadSourcePolicy.DIRECT_HTTP, decision.policy)
    }

    /** DIRECT_HTTP 策略 + 不合法 URL → 回退到 FALLBACK_SIMULATED。 */
    @Test
    fun testResolveDirectHttp_withInvalidUrl_fallsBack() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true)
        )
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, ftpUrl)
        assertEquals(DownloadSourcePolicy.FALLBACK_SIMULATED, decision.policy)
        assertEquals(DownloaderText.UNSUPPORTED_URL_PROTOCOL, decision.reason)
    }

    /** DIRECT_HTTP 策略 + 环境禁用 → 回退到 FALLBACK_SIMULATED。 */
    @Test
    fun testResolveDirectHttp_disabled_fallsBack() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(allowDirectHttp = false, allowMockSource = true)
        )
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, httpUrl)
        assertEquals(DownloadSourcePolicy.FALLBACK_SIMULATED, decision.policy)
        assertEquals(DownloaderText.DIRECT_HTTP_DISABLED, decision.reason)
    }

    /** MOCK 策略 + 环境允许 → 返回 MOCK 源。 */
    @Test
    fun testResolveMock_enabled_returnsMock() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true)
        )
        val decision = resolver.resolve(DownloadSourcePolicy.MOCK, httpUrl)
        assertEquals(DownloadSourcePolicy.MOCK, decision.policy)
        assertEquals(DownloaderText.MOCK_ENABLED, decision.reason)
    }

    /** MOCK 策略 + 环境禁用 → 回退到 FALLBACK_SIMULATED。 */
    @Test
    fun testResolveMock_disabled_fallsBack() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = false)
        )
        val decision = resolver.resolve(DownloadSourcePolicy.MOCK, httpUrl)
        assertEquals(DownloadSourcePolicy.FALLBACK_SIMULATED, decision.policy)
        assertEquals(DownloaderText.MOCK_DISABLED, decision.reason)
    }

    /** FALLBACK_SIMULATED 策略 → 直接返回回退模拟。 */
    @Test
    fun testResolveFallback_returnsFallback() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true)
        )
        val decision = resolver.resolve(DownloadSourcePolicy.FALLBACK_SIMULATED, httpUrl)
        assertEquals(DownloadSourcePolicy.FALLBACK_SIMULATED, decision.policy)
        assertEquals(DownloaderText.FALLBACK_SIMULATED, decision.reason)
    }
}
