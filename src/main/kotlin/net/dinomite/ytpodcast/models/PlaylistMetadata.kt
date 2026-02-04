package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

/**
 * Represents metadata for a YouTube playlist as returned by yt-dlp.
 *
 * @property id The YouTube playlist ID
 * @property title The playlist title
 * @property description The playlist description
 * @property uploader Name of the channel that owns the playlist
 * @property thumbnail URL to the playlist thumbnail
 * @property entries List of videos in the playlist
 */
@Serializable
data class PlaylistMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val entries: List<VideoMetadata> = emptyList(),
)
