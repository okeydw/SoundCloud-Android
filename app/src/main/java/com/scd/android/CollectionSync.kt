package com.scd.android

import kotlinx.serialization.Serializable

@Serializable
data class CollectionSync(
    val status: String? = null,
    val lastCompletedAt: String? = null,
    val baselineGeneration: Long? = null,
    val observedAt: String? = null,
) {
    val snapshotReady: Boolean
        get() = status == null || status == "ready" || status == "synced" ||
            lastCompletedAt != null || (baselineGeneration ?: 0) > 0 || observedAt != null
}
