package com.xzq.appstore.common.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdDiffCallbackTest {

    @Test
    fun `内容比较使用调用方定义的比较规则`() {
        val callback = object : AppIdDiffCallback<DiffItem>(
            idOf = { it.appId },
            contentsSame = { old, new -> old.displayName == new.displayName },
        ) {}

        assertTrue(callback.areItemsTheSame(DiffItem("app", "旧名称", 1), DiffItem("app", "新名称", 2)))
        assertFalse(callback.areContentsTheSame(DiffItem("app", "旧名称", 1), DiffItem("app", "新名称", 1)))
    }

    private data class DiffItem(val appId: String, val displayName: String, val versionCode: Int)
}
