package com.nio.appstore.core.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DownloaderTextTest 验证下载器文案格式化方法的正确性。
 */
class DownloaderTextTest {

    /** 验证文件长度不匹配文案的格式化输出。 */
    @Test
    fun testFileLengthMismatch_format() {
        val result = DownloaderText.fileLengthMismatch(expectedBytes = 1024L, actualBytes = 512L)
        // 应包含期望和实际值
        assertEquals("文件长度不一致，期望 1024，实际 512", result)
    }

    /** 验证校验值不匹配文案的格式化输出。 */
    @Test
    fun testChecksumMismatch_format() {
        val result = DownloaderText.checksumMismatch("md5")
        // 校验类型应转为大写
        assertEquals("文件校验失败，MD5 不匹配", result)
    }
}
