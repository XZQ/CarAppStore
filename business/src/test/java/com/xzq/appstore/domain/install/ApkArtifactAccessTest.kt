package com.xzq.appstore.domain.install

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApkArtifactAccessTest {
    @Test fun `cleanup skips APK held by installer and exception releases access`() = runBlocking {
        val access = ApkArtifactAccess()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val installing = async { access.tryUse("app") { entered.complete(Unit); finish.await() } }
        entered.await()
        assertFalse(access.tryUse("app") { error("must not delete active APK") })
        assertTrue(access.tryUse("other") {})
        finish.complete(Unit)
        installing.await()
        try { access.tryUse("app") { error("failed operation") } } catch (_: IllegalStateException) { }
        assertTrue(access.tryUse("app") {})
    }
}
