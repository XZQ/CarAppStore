package com.nio.appstore.data.datasource.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppCatalogCacheMetadataStoreTest {

    @Test
    fun `read 在文件不存在时返回 null`() {
        val workDir = Files.createTempDirectory("catalog-meta-missing").toFile()
        val target = File(workDir, "catalog.meta.json")

        val result = AppCatalogCacheMetadataStore.read(target)

        assertNull(result)
    }

    @Test
    fun `write 后可以完整读取元数据`() {
        val workDir = Files.createTempDirectory("catalog-meta-write").toFile()
        val target = File(workDir, "catalog.meta.json")
        val metadata = AppCatalogCacheMetadata(
            eTag = "etag-v2",
            lastModified = "Thu, 16 Apr 2026 11:30:00 GMT",
        )

        AppCatalogCacheMetadataStore.write(target, metadata)
        val result = AppCatalogCacheMetadataStore.read(target)

        assertEquals(metadata, result)
    }
}
