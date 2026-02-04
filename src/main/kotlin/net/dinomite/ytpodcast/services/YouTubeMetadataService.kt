package net.dinomite.ytpodcast.services

import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.YtDlpExecutor

/**
 * Service for fetching YouTube metadata.
 *
 * Provides a clean API for retrieving playlist and video metadata
 * by delegating to the underlying YtDlpExecutor.
 *
 * @property ytDlpExecutor The executor for yt-dlp CLI commands
 */
class YouTubeMetadataService(private val ytDlpExecutor: YtDlpExecutor) {
    /**
     * Fetches metadata for a YouTube playlist.
     *
     * @param playlistId The YouTube playlist ID
     * @return Playlist metadata including video entries
     */
    fun getPlaylist(playlistId: String): PlaylistMetadata = ytDlpExecutor.fetchPlaylist(playlistId)

    /**
     * Fetches metadata for a single YouTube video.
     *
     * @param videoId The YouTube video ID
     * @return Video metadata
     */
    fun getVideo(videoId: String): VideoMetadata = ytDlpExecutor.fetchVideo(videoId)
}
