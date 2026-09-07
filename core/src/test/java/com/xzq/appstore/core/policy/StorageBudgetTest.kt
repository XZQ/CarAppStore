package com.xzq.appstore.core.policy

import org.junit.Assert.*
import org.junit.Test

class StorageBudgetTest {
    @Test fun `download includes remaining parts merge copy and reserve without overflow`() {
        val reserve = StorageBudget.RESERVE_BYTES
        assertTrue(StorageBudget.canDownload(reserve + 150, 100, 50))
        assertFalse(StorageBudget.canDownload(reserve + 149, 100, 50))
        assertFalse(StorageBudget.canDownload(Long.MAX_VALUE, Long.MAX_VALUE, 0))
        assertFalse(StorageBudget.canInstall(Long.MAX_VALUE, Long.MAX_VALUE))
        assertFalse(StorageBudget.canDownload(-1, 100, 0))
        assertTrue(StorageBudget.canInstall(reserve + 200, 100))
        assertFalse(StorageBudget.canInstall(reserve + 199, 100))
    }
}
