package com.xzq.appstore.core.downloader

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
     * 合并前会对每个分片做长度校验：若 [DownloadSegmentRecord.endByte] >= [DownloadSegmentRecord.startByte]，
     * 则要求 partFile 长度等于 (endByte - startByte + 1)，否则视为上次写入中途崩溃，抛 IOException 让
     * 上层以 MERGE_FAILED 形式失败，避免错位以 CHECKSUM_MISMATCH 形式出现丢失诊断。
     *
     * @param segments 按 index 排序前的分片记录列表
     * @param finalFile 合并目标文件
     * @return 成功返回 null；失败返回诊断信息（缺分片、长度错位、写盘错误等），供上层保留可追踪的失败原因
     */
    fun mergeSegments(segments: List<DownloadSegmentRecord>, finalFile: File): String? = runCatching {
        RandomAccessFile(finalFile, "rw").use { out ->
            out.setLength(0L)
            segments.sortedBy { it.index }.forEach { seg ->
                val partFile = File(seg.tmpFilePath)
                if (!partFile.exists()) {
                    throw IOException("missing segment ${seg.index}")
                }
                val expected = if (seg.endByte >= seg.startByte) {
                    seg.endByte - seg.startByte + 1L
                } else {
                    -1L
                }
                val actual = partFile.length()
                if (expected > 0L && actual != expected) {
                    throw IOException("segment ${seg.index} length mismatch: expected=$expected actual=$actual")
                }
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
    }.fold(onSuccess = { null }, onFailure = { it.message ?: "segment merge failed" })

    /**
     * 校验下载文件的长度和可选摘要值。
     *
     * @param file 待校验文件
     * @param expectedBytes 期望的文件字节数，<= 0 表示不校验长度
     * @param checksumType 校验算法名称，null 表示不校验摘要
     * @param checksumValue 期望的摘要值，null 表示不校验摘要
     * @return 校验结果
     */
    fun verifyFile(file: File, expectedBytes: Long, checksumType: String?, checksumValue: String?): VerificationResult {
        if (!file.exists()) {
            return VerificationResult(false, DownloadFailureCode.FILE_MISSING, DownloaderText.FILE_NOT_EXISTS)
        }
        val actual = file.length()
        if (expectedBytes > 0L && actual != expectedBytes) {
            return VerificationResult(false, DownloadFailureCode.FILE_INCOMPLETE, DownloaderText.fileLengthMismatch(expectedBytes, actual))
        }
        if (!checksumType.isNullOrBlank() && !checksumValue.isNullOrBlank()) {
            // 未知算法不能静默换成 SHA-256 再比较：那会把配置错误误诊为产物损坏，
            // 且产物级失败重试会清理分片缓存，错误诊断会放大成不必要的重复下载。
            if (supportedAlgorithm(checksumType) == null) {
                return VerificationResult(false, DownloadFailureCode.CHECKSUM_MISMATCH, DownloaderText.unsupportedChecksumType(checksumType))
            }
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
     * @throws IllegalArgumentException 算法不受支持时抛出，不做静默回退
     */
    fun calculateHash(file: File, checksumType: String): String {
        val algo = requireNotNull(supportedAlgorithm(checksumType)) { "Unsupported checksum type: $checksumType" }
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

    /** 将校验算法名称归一化为 MessageDigest 标准名；不受支持时返回 null。 */
    private fun supportedAlgorithm(checksumType: String): String? = when (checksumType.uppercase()) {
        ALGO_SHA256, ALGO_SHA256_ALT -> ALGO_SHA256
        ALGO_MD5 -> ALGO_MD5
        else -> null
    }
}

/**
 * 文件校验结果。
 *
 * @property ok 校验是否通过
 * @property code 校验失败时对应的失败码
 * @property message 校验失败时返回的详细文案
 */
data class VerificationResult(val ok: Boolean, val code: DownloadFailureCode = DownloadFailureCode.UNKNOWN, val message: String = "")
