package com.nio.appstore.core.downloader

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * 下载文件辅助工具，负责分片合并、文件校验和哈希计算。
 *
 * 将文件操作从 RealFileDownloader 中抽离，使下载器专注于下载流程编排。
 */
object DownloadFileHelper {

    /** 合并分片时使用的缓冲区大小。 */
    private const val MERGE_BUFFER_BYTES = 8 * 1024

    /** SHA-256 算法标准名称。 */
    private const val ALGO_SHA256 = "SHA-256"

    /** SHA-256 算法备用名称（无连字符）。 */
    private const val ALGO_SHA256_ALT = "SHA256"

    /** MD5 算法名称。 */
    private const val ALGO_MD5 = "MD5"

    /**
     * 将所有分片文件按顺序合并成最终文件。
     *
     * @param segments 按 index 排序前的分片记录列表
     * @param finalFile 合并目标文件
     * @return 合并是否成功
     */
    fun mergeSegments(
        segments: List<DownloadSegmentRecord>,
        finalFile: File,
    ): Boolean {
        return runCatching {
            RandomAccessFile(finalFile, "rw").use { out ->
                out.setLength(0L)
                segments.sortedBy { it.index }.forEach { seg ->
                    val partFile = File(seg.tmpFilePath)
                    if (!partFile.exists()) throw IOException("missing segment ${seg.index}")
                    partFile.inputStream().use { input ->
                        val buffer = ByteArray(MERGE_BUFFER_BYTES)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            if (read > 0) {
                                out.write(buffer, 0, read)
                            }
                            read = input.read(buffer)
                        }
                    }
                }
            }
        }.isSuccess
    }

    /**
     * 校验下载文件的长度和可选摘要值。
     *
     * @param file 待校验文件
     * @param expectedBytes 期望的文件字节数，<= 0 表示不校验长度
     * @param checksumType 校验算法名称，null 表示不校验摘要
     * @param checksumValue 期望的摘要值，null 表示不校验摘要
     * @return 校验结果
     */
    fun verifyFile(
        file: File,
        expectedBytes: Long,
        checksumType: String?,
        checksumValue: String?,
    ): VerificationResult {
        if (!file.exists()) {
            return VerificationResult(false, DownloadFailureCode.FILE_MISSING, DownloaderText.FILE_NOT_EXISTS)
        }
        val actual = file.length()
        if (expectedBytes > 0L && actual != expectedBytes) {
            return VerificationResult(false, DownloadFailureCode.FILE_INCOMPLETE, DownloaderText.fileLengthMismatch(expectedBytes, actual))
        }
        if (!checksumType.isNullOrBlank() && !checksumValue.isNullOrBlank()) {
            val actualHash = calculateHash(file, checksumType)
            if (!actualHash.equals(checksumValue, ignoreCase = true)) {
                return VerificationResult(false, DownloadFailureCode.CHECKSUM_MISMATCH, DownloaderText.checksumMismatch(checksumType))
            }
        }
        return VerificationResult(true)
    }

    /**
     * 根据配置的摘要算法计算文件哈希值。
     *
     * @param file 待计算文件
     * @param checksumType 校验算法名称，支持 SHA-256/SHA256/MD5
     * @return 十六进制格式的小写哈希值
     */
    fun calculateHash(file: File, checksumType: String): String {
        val algo = when (checksumType.uppercase()) {
            ALGO_SHA256, ALGO_SHA256_ALT -> ALGO_SHA256
            ALGO_MD5 -> ALGO_MD5
            else -> ALGO_SHA256
        }
        val digest = MessageDigest.getInstance(algo)
        file.inputStream().use { input ->
            val buffer = ByteArray(MERGE_BUFFER_BYTES)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * 文件校验结果。
 *
 * @property ok 校验是否通过
 * @property code 校验失败时对应的失败码
 * @property message 校验失败时返回的详细文案
 */
data class VerificationResult(
    val ok: Boolean,
    val code: DownloadFailureCode = DownloadFailureCode.UNKNOWN,
    val message: String = "",
)
