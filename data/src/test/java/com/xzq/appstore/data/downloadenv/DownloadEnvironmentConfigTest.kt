package com.xzq.appstore.data.downloadenv

import com.xzq.appstore.core.downloader.DownloadSourcePolicy
import com.xzq.appstore.data.datasource.remote.DownloadSourceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DownloadEnvironmentConfigTest 验证生产配置不会静默回退到示例或本地模拟地址。
 */
class DownloadEnvironmentConfigTest {
    /** 未注入生产变量时不再生成 example 域名。 */
    @Test
    fun `production config never falls back to example endpoints`() {
        val config = DownloadEnvironmentConfig.forEnvironment(DownloadEnvironment.PROD)

        assertFalse(config.catalogEndpointUrl.orEmpty().contains("example", ignoreCase = true))
        assertFalse(config.downloadBaseUrl.contains("example", ignoreCase = true))
        if (config.catalogEndpointUrl.isNullOrBlank()) {
            assertNull(config.catalogEndpointUrl)
        }
    }

    /** 下载基地址为空时不拼出可被误认为有效地址的相对路径。 */
    @Test
    fun `catalog keeps apk url empty when production base url is missing`() {
        val config = DownloadEnvironmentConfig(
            environment = DownloadEnvironment.PROD,
            defaultSourcePolicy = DownloadSourcePolicy.DIRECT_HTTP,
            allowMockSource = false,
            downloadBaseUrl = "",
        )

        assertEquals("", DownloadSourceCatalog(config).get("sample_app").apkUrl)
    }
}
