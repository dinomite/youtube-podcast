package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

/**
 * Represents metadata for a YouTube playlist as returned by yt-dlp.
 *
 * @property id The YouTube playlist ID
 * @property title The playlist title
 * @property description The playlist description
 * @property uploader Name of the channel that owns the playlist
 * @property thumbnail Flat thumbnail URL, used as fallback when [thumbnails] is empty
 * @property thumbnails List of available thumbnails with resolution metadata
 * @property entries List of videos in the playlist
 */
@Serializable
data class PlaylistMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
    val entries: List<VideoMetadata> = emptyList(),
) {
    /** The URL of the highest-resolution thumbnail, falling back to [thumbnail] if [thumbnails] is empty. */
    val bestThumbnail: String?
        get() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: thumbnail
}
