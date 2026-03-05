package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class CacheStats(
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val maxFiles: Int,
    val maxSizeBytes: Long,
)
