package net.dinomite.ytpodcast.controller

import io.klogging.Klogging
import net.dinomite.ytpodcast.service.AudioConverterService
import net.dinomite.ytpodcast.service.RssFeedService
import net.dinomite.ytpodcast.service.YouTubeService

class PlaylistController(
    private val youTubeService: YouTubeService,
    private val rssFeedService: RssFeedService,
    private val audioConverterService: AudioConverterService
) : Klogging {
    suspend fun playlistFeed(baseUrl: String, playlistId: String): String {
        logger.info("Fetching RSS feed for playlist: $playlistId")

        val playlistInfo = youTubeService.getPlaylistInfo(playlistId)

        return rssFeedService.generatePodcastFeed(playlistInfo, baseUrl)
    }

    suspend fun audioFile(showId: String, episodeId: String): ByteArray {
        logger.info("Fetching audio for episode $episodeId from show $showId")

        // Get audio from YouTube
        val audioData = youTubeService.getAudioStream(episodeId)

        // Check if conversion is needed
        return if (audioConverterService.isValidAudioFormat(audioData)) {
            // Convert to MP3
            logger.info("Converting audio to MP3 for episode $episodeId")
            val converted = audioConverterService.convertToMp3(audioData, episodeId)
            converted.data
        } else {
            logger.warn("Invalid audio format for episode $episodeId, attempting conversion anyway")
            val converted = audioConverterService.convertToMp3(audioData, episodeId)
            converted.data
        }
    }

//    suspend fun playlistInfo(call: RoutingCall) = try {
//        val playlistId = call.parameters["playlistId"]
//            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "Missing playlist ID"))
//
//        logger.info("Getting info for playlist: $playlistId")
//
//        val playlistInfo = youTubeService.getPlaylistInfo(playlistId)
//
//        call.respond(playlistInfo)
//    } catch (e: Exception) {
//        logger.error("Failed to get playlist info", e)
//        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Failed to get playlist info"))
//    }
}
