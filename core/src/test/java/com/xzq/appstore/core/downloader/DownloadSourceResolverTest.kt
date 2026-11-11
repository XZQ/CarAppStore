package com.xzq.appstore.core.downloader

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
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true))
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, httpUrl)
        assertEquals(DownloadSourcePolicy.DIRECT_HTTP, decision.policy)
        assertEquals(DownloaderText.DIRECT_HTTP_ENABLED, decision.reason)
    }

    /** DIRECT_HTTP 策略 + 合法 https URL → 返回 DIRECT_HTTP 源。 */
    @Test
    fun testResolveDirectHttp_withHttpsUrl_returnsDirectHttp() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true))
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, httpsUrl)
        assertEquals(DownloadSourcePolicy.DIRECT_HTTP, decision.policy)
    }

    /** DIRECT_HTTP 策略 + 不合法 URL → 回退到 FALLBACK_SIMULATED。 */
    @Test
    fun testResolveDirectHttp_withInvalidUrl_fallsBack() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true))
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, ftpUrl)
        assertEquals(DownloadSourcePolicy.FALLBACK_SIMULATED, decision.policy)
        assertEquals(DownloaderText.UNSUPPORTED_URL_PROTOCOL, decision.reason)
    }

    /** DIRECT_HTTP 策略 + 环境禁用 → 回退到 FALLBACK_SIMULATED。 */
    @Test
    fun testResolveDirectHttp_disabled_fallsBack() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = false, allowMockSource = true))
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, httpUrl)
        assertEquals(DownloadSourcePolicy.FALLBACK_SIMULATED, decision.policy)
        assertEquals(DownloaderText.DIRECT_HTTP_DISABLED, decision.reason)
    }

    /** MOCK 策略 + 环境允许 → 返回 MOCK 源。 */
    @Test
    fun testResolveMock_enabled_returnsMock() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true))
        val decision = resolver.resolve(DownloadSourcePolicy.MOCK, httpUrl)
        assertEquals(DownloadSourcePolicy.MOCK, decision.policy)
        assertEquals(DownloaderText.MOCK_ENABLED, decision.reason)
    }

    /** MOCK 策略 + 环境禁用模拟 → 拒绝，而不是回退模拟产出假产物。 */
    @Test
    fun testResolveMock_disabled_rejected() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = false))
        val decision = resolver.resolve(DownloadSourcePolicy.MOCK, httpUrl)
        assertEquals(DownloadSourcePolicy.REJECTED, decision.policy)
        assertEquals(DownloaderText.MOCK_DISABLED, decision.reason)
    }

    /** FALLBACK_SIMULATED 策略 → 直接返回回退模拟。 */
    @Test
    fun testResolveFallback_returnsFallback() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = true))
        val decision = resolver.resolve(DownloadSourcePolicy.FALLBACK_SIMULATED, httpUrl)
        assertEquals(DownloadSourcePolicy.FALLBACK_SIMULATED, decision.policy)
        assertEquals(DownloaderText.FALLBACK_SIMULATED, decision.reason)
    }

    /** 生产环境禁止模拟：FALLBACK_SIMULATED 必须被拒绝，而不是产出假产物。 */
    @Test
    fun testResolveFallback_mockDisabled_rejected() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = false))
        val decision = resolver.resolve(DownloadSourcePolicy.FALLBACK_SIMULATED, httpsUrl)
        assertEquals(DownloadSourcePolicy.REJECTED, decision.policy)
    }

    /** 生产环境禁止模拟：DIRECT_HTTP 遇到非法 URL 不能回退模拟，必须拒绝。 */
    @Test
    fun testResolveDirectHttp_invalidUrl_mockDisabled_rejected() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = true, allowMockSource = false))
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, ftpUrl)
        assertEquals(DownloadSourcePolicy.REJECTED, decision.policy)
    }

    /** 生产环境禁止模拟：DIRECT_HTTP 被环境禁用时同样拒绝，而不是回退模拟。 */
    @Test
    fun testResolveDirectHttp_disabled_mockDisabled_rejected() {
        val resolver = DownloadSourceResolver(DownloadSourceResolverConfig(allowDirectHttp = false, allowMockSource = false))
        val decision = resolver.resolve(DownloadSourcePolicy.DIRECT_HTTP, httpsUrl)
        assertEquals(DownloadSourcePolicy.REJECTED, decision.policy)
    }

    /** 请求未声明策略时使用环境默认：生产默认 DIRECT_HTTP + 合法 URL 走真实下载。 */
    @Test
    fun testResolveNullPolicy_usesEnvironmentDefault() {
        val resolver = DownloadSourceResolver(
            DownloadSourceResolverConfig(defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP, allowDirectHttp = true, allowMockSource = false),
        )
        val decision = resolver.resolve(null, httpsUrl)
        assertEquals(DownloadSourcePolicy.DIRECT_HTTP, decision.policy)
    }
}
