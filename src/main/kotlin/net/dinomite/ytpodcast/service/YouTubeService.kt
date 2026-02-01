package net.dinomite.ytpodcast.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.request.RequestPlaylistInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dinomite.ytpodcast.models.PlaylistInfo
import net.dinomite.ytpodcast.models.ResponseVideoInfo
import org.slf4j.LoggerFactory

class YouTubeService {
    private val logger = LoggerFactory.getLogger(YouTubeService::class.java)
    private val downloader = YoutubeDownloader()

    // Cache for playlist information (1 hour TTL)
    private val playlistCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build<String, PlaylistInfo>()

    // Cache for audio data (30 minutes TTL)
    private val audioCache = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .weigher<String, ByteArray> { _, value -> value.size }
        .maximumWeight(500_000_000) // 500MB max cache size
        .build<String, ByteArray>()

    suspend fun getPlaylistInfo(playlistUrl: String): PlaylistInfo = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(playlistUrl)
            ?: throw IllegalArgumentException("Invalid playlist URL")

        playlistCache.get(playlistId) {
            fetchPlaylistInfo(playlistId)
        }!!
    }

//    suspend fun getVideoInfo(videoId: String): ResponseVideoInfo = withContext(Dispatchers.IO) {
//        val request = RequestVideoInfo(videoId)
//        val response = downloader.getVideoInfo(request)
//        val video = response.data()
//
//        video.details()?.let { details ->
//            ResponseVideoInfo(
//                id = videoId,
//                title = details.title() ?: "Unknown Title",
//                description = details.description(),
//                author = details.author(),
//                duration = details.lengthSeconds()?.toLong(),
//                publishedAt = null, // Not available in JavaTube
//                thumbnailUrl = details.thumbnails()?.firstOrNull()
//            )
//        } ?: error("Failed to fetch video info for $videoId")
//    }

    suspend fun getAudioStream(videoId: String): ByteArray = withContext(Dispatchers.IO) {
        audioCache.get(videoId) {
            fetchAudioStream(videoId)
        }!!
    }

    private fun fetchPlaylistInfo(playlistId: String): PlaylistInfo {
        logger.info("Fetching playlist info for ID: $playlistId")

        val request = RequestPlaylistInfo(playlistId)
        val response = downloader.getPlaylistInfo(request)
        val playlist = response.data()

        val details = playlist.details()
            ?: error("Failed to fetch playlist details")

        val videos = playlist.videos().mapNotNull { video ->
            video?.let { videoDetails ->
                ResponseVideoInfo(
                    id = videoDetails.videoId() ?: return@let null,
                    title = videoDetails.title() ?: "Unknown Title",
                    description = null, // Not available in playlist video details
                    author = videoDetails.author(),
                    duration = videoDetails.lengthSeconds().toLong(),
                    publishedAt = null,
                    thumbnailUrl = videoDetails.thumbnails()?.firstOrNull()
                )
            }
        }

        return PlaylistInfo(
            id = playlistId,
            title = details.title() ?: "Unknown Playlist",
            description = details.author(),
            author = details.author(),
            videos = videos
        )
    }

    private fun fetchAudioStream(videoId: String): ByteArray {
        logger.info("Fetching audio stream for video ID: $videoId")

        val response = downloader.getVideoInfo(RequestVideoInfo(videoId))
        val video = response.data()

        val audioFormat = video.audioFormats()
            ?.maxByOrNull { it.bitrate() ?: 0 }
            ?: error("No audio format available for video $videoId")

        val outputStream = ByteArrayOutputStream()
        val downloadRequest = RequestVideoStreamDownload(audioFormat, outputStream)
        downloader.downloadVideoStream(downloadRequest)

        return outputStream.toByteArray()
    }

    @Suppress("Detekt:ReturnCount")
    private fun extractPlaylistId(url: String): String? {
        // Extract playlist ID from various YouTube playlist URL formats
        val patterns = listOf(
            Regex("list=([a-zA-Z0-9_-]+)"),
            Regex("playlist\\?list=([a-zA-Z0-9_-]+)"),
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // If no pattern matches, assume it might be just the playlist ID
        if (url.matches(Regex("[a-zA-Z0-9_-]+"))) {
            return url
        }

        return null
    }

    @Suppress("Detekt:ReturnCount")
    fun extractVideoId(url: String): String? {
        // Extract video ID from various YouTube video URL formats
        val patterns = listOf(
            Regex("v=([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Regex("embed/([a-zA-Z0-9_-]{11})"),
            Regex("watch/([a-zA-Z0-9_-]{11})")
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // If no pattern matches, assume it might be just the video ID
        if (url.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return url
        }

        return null
    }
}
