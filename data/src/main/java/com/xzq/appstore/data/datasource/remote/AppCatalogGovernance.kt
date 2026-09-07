package com.xzq.appstore.data.datasource.remote

import java.nio.ByteBuffer
import java.security.MessageDigest

enum class CatalogListingState {
    ACTIVE, HIDDEN, REMOVED, ROLLBACK,
}

data class AppCatalogGovernance(
    val listingState: CatalogListingState = CatalogListingState.ACTIVE,
    val rolloutPercent: Int = FULL_ROLLOUT_PERCENT,
    val allowedChannels: List<String> = emptyList(),
    val blockedChannels: List<String> = emptyList(),
    val rollbackVersion: String = "",
) {
    fun isVisible(appId: String, channel: String, installationId: String, releaseId: String): Boolean {
        if (listingState == CatalogListingState.HIDDEN || listingState == CatalogListingState.REMOVED) {
            return false
        }
        val normalizedChannel = channel.trim().lowercase()
        if (allowedChannels.isNotEmpty() && normalizedChannel !in allowedChannels.map { it.trim().lowercase() }) {
            return false
        }
        if (normalizedChannel in blockedChannels.map { it.trim().lowercase() }) {
            return false
        }
        return rolloutBucket(installationId, appId, releaseId) < rolloutPercent.coerceIn(0, FULL_ROLLOUT_PERCENT)
    }

    fun effectiveVersion(versionName: String): String {
        return if (listingState == CatalogListingState.ROLLBACK && rollbackVersion.isNotBlank()) {
            rollbackVersion
        } else {
            versionName
        }
    }

    /** 同一次安装、应用和发布保持稳定；提升百分比不会把已入组实例移出。 */
    internal fun rolloutBucket(installationId: String, appId: String, releaseId: String): Int {
        require(installationId.isNotBlank()) { "Missing catalog installation cohort" }
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(installationId, appId, releaseId).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        val unsigned = ByteBuffer.wrap(digest.digest()).int.toLong() and 0xffff_ffffL
        return (unsigned % FULL_ROLLOUT_PERCENT).toInt()
    }

    private companion object {
        private const val FULL_ROLLOUT_PERCENT = 100
    }
}
