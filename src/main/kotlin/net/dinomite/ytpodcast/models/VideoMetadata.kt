package net.dinomite.ytpodcast.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents metadata for a YouTube video as returned by yt-dlp.
 *
 * @property id The YouTube video ID
 * @property title The video title
 * @property description The video description
 * @property thumbnail URL to the video thumbnail
 * @property duration Video duration in seconds
 * @property uploadDate Upload date in YYYYMMDD format
 * @property uploader Name of the channel that uploaded the video
 * @property playlistId Playlist ID if this video is part of a playlist
 * @property playlistTitle Playlist title if this video is part of a playlist
 * @property playlistUploader Playlist uploader if this video is part of a playlist
 */
@Serializable
data class VideoMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    val uploader: String? = null,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("playlist_title") val playlistTitle: String? = null,
    @SerialName("playlist_uploader") val playlistUploader: String? = null,
)
