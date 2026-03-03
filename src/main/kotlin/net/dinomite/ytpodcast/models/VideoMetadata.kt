package net.dinomite.ytpodcast.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
    val duration: Int? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    val uploader: String? = null,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("playlist_title") val playlistTitle: String? = null,
    @SerialName("playlist_uploader") val playlistUploader: String? = null,
) {
    val bestThumbnail: String?
        get() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: thumbnail
}
