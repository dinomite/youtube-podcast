package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int? = null,
    val width: Int? = null,
    val id: String? = null,
    val resolution: String? = null,
)
