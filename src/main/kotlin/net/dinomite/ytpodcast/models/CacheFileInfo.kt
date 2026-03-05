package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class CacheFileInfo(
    val videoId: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
)
