package net.dinomite.ytpodcast.testsupport

import java.io.File
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.YtDlpException
import net.dinomite.ytpodcast.util.YtDlpExecutor

class StubYtDlpExecutor : YtDlpExecutor() {
    private val playlists = mutableMapOf<String, PlaylistMetadata>()
    private val videos = mutableMapOf<String, VideoMetadata>()
    private val audioContent = mutableMapOf<String, ByteArray>()

    fun givenPlaylist(playlistId: String, metadata: PlaylistMetadata) {
        playlists[playlistId] = metadata
    }

    fun givenVideo(videoId: String, metadata: VideoMetadata) {
        videos[videoId] = metadata
    }

    fun givenAudio(videoId: String, content: ByteArray) {
        audioContent[videoId] = content
    }

    override fun fetchPlaylist(playlistId: String): PlaylistMetadata = playlists[playlistId]
        ?: throw YtDlpException("yt-dlp failed with exit code 1: Playlist not found: $playlistId")

    override fun fetchVideo(videoId: String): VideoMetadata = videos[videoId]
        ?: throw YtDlpException("yt-dlp failed with exit code 1: Video not found: $videoId")

    override fun downloadAudio(videoId: String, outputFile: File) {
        val content = audioContent[videoId]
            ?: throw YtDlpException("yt-dlp failed with exit code 1: Video unavailable: $videoId")
        outputFile.writeBytes(content)
    }
}
