package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

data class PlaylistInfo(
    val id: String,
    val title: String,
    val description: String?,
    val author: String?,
    val videos: List<ResponseVideoInfo>
)

@Serializable
data class ResponseVideoInfo(
    val id: String,
    val title: String,
    val description: String?,
    val author: String?,
    val duration: Long?, // in seconds
    val publishedAt: Long?, // timestamp
    val thumbnailUrl: String?
)

@Serializable
data class PodcastFeedRequest(val playlistUrl: String)

@Serializable
data class ErrorResponse(val error: String, val message: String)

data class AudioData(val data: ByteArray, val contentType: String = "audio/mpeg") {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioData

        if (!data.contentEquals(other.data)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}
