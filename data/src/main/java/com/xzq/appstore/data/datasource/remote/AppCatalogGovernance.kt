package com.xzq.appstore.data.datasource.remote

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
    fun isVisible(appId: String, channel: String): Boolean {
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
        return rolloutBucket(appId) < rolloutPercent.coerceIn(0, FULL_ROLLOUT_PERCENT)
    }

    fun effectiveVersion(versionName: String): String {
        return if (listingState == CatalogListingState.ROLLBACK && rollbackVersion.isNotBlank()) {
            rollbackVersion
        } else {
            versionName
        }
    }

    private fun rolloutBucket(appId: String): Int {
        return appId.fold(0) { acc, char -> (acc * HASH_MULTIPLIER + char.code) and Int.MAX_VALUE } % FULL_ROLLOUT_PERCENT
    }

    private companion object {
        private const val FULL_ROLLOUT_PERCENT = 100
        private const val HASH_MULTIPLIER = 31
    }
}
