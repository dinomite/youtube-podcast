package net.dinomite.ytpodcast.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.models.ErrorResponse
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.RssFeedService
import net.dinomite.ytpodcast.services.YouTubeMetadataService
import net.dinomite.ytpodcast.util.UrlBuilder
import net.dinomite.ytpodcast.util.YtDlpException
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.slf4j.LoggerFactory

fun Application.configureRouting(
    appConfig: AppConfig,
    ytDlpExecutor: YtDlpExecutor = YtDlpExecutor(),
) {
    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val urlBuilder = UrlBuilder(appConfig.baseUrl)
    val rssFeedService = RssFeedService(urlBuilder)
    val audioService = AudioService(ytDlpExecutor)

    val handlers = RouteHandlers(youTubeMetadataService, rssFeedService, audioService)

    routing {
        get("/") {
            call.respondText("YouTube to Podcast RSS Feed Converter")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        handlers.registerShowRoute(this)
        handlers.registerEpisodeRoute(this)
    }
}

/**
 * Holds route handler implementations with their dependencies.
 */
private class RouteHandlers(
    private val youTubeMetadataService: YouTubeMetadataService,
    private val rssFeedService: RssFeedService,
    private val audioService: AudioService,
) {
    private val logger = LoggerFactory.getLogger("Routing")

    fun registerShowRoute(routing: Routing) {
        routing.get("/show/{playlistId}") {
            val playlistId = call.parameters["playlistId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("bad_request", "Missing playlistId"),
                )

            handleShowRequest(call, playlistId)
        }
    }

    fun registerEpisodeRoute(routing: Routing) {
        routing.get("/episode/{videoId}.mp3") {
            val videoId = call.parameters["videoId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("bad_request", "Missing videoId"),
                )

            handleEpisodeRequest(call, videoId)
        }
    }

    private suspend fun handleShowRequest(call: ApplicationCall, playlistId: String) {
        try {
            val playlist = youTubeMetadataService.getPlaylist(playlistId)
            val (scheme, host, port) = requestOrigin(call)
            val rssFeed = rssFeedService.generateFeed(playlist, scheme, host, port)
            call.respondText(rssFeed, ContentType.Application.Rss)
        } catch (e: YtDlpException) {
            logger.error("Failed to fetch playlist $playlistId", e)
            respondToYtDlpError(
                call = call,
                exception = e,
                errorConfig = YtDlpErrorConfig(
                    notFoundCode = "not_found",
                    notFoundMessage = "Playlist not found: $playlistId",
                    errorCode = "fetch_error",
                    errorPrefix = "Failed to fetch playlist",
                ),
            )
        }
    }

    private suspend fun handleEpisodeRequest(call: ApplicationCall, videoId: String) {
        var tempFile: java.io.File? = null
        try {
            tempFile = audioService.downloadToTempFile(videoId)
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$videoId.mp3\"")
            call.respondFile(tempFile)
        } catch (e: YtDlpException) {
            logger.error("Failed to download episode $videoId", e)
            respondToYtDlpError(
                call = call,
                exception = e,
                errorConfig = YtDlpErrorConfig(
                    notFoundCode = "not_found",
                    notFoundMessage = "Video not found: $videoId",
                    errorCode = "download_error",
                    errorPrefix = "Failed to download episode",
                    additionalNotFoundKeywords = listOf("private"),
                ),
            )
        } finally {
            tempFile?.delete()
        }
    }

    private suspend fun respondToYtDlpError(
        call: ApplicationCall,
        exception: YtDlpException,
        errorConfig: YtDlpErrorConfig,
    ) {
        val notFoundKeywords = listOf("unavailable", "not found") + errorConfig.additionalNotFoundKeywords
        val isNotFound = notFoundKeywords.any { exception.message?.contains(it, ignoreCase = true) == true }

        if (isNotFound) {
            val notFoundResponse = ErrorResponse(errorConfig.notFoundCode, errorConfig.notFoundMessage)
            call.respond(HttpStatusCode.NotFound, notFoundResponse)
        } else {
            val message = "${errorConfig.errorPrefix}: ${exception.message}"
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(errorConfig.errorCode, message))
        }
    }

    private fun requestOrigin(call: ApplicationCall): Triple<String, String, Int> {
        val scheme = call.request.local.scheme
        val host = call.request.host()
        val port = call.request.port()
        return Triple(scheme, host, port)
    }
}

/**
 * Configuration for yt-dlp error response handling.
 */
private data class YtDlpErrorConfig(
    val notFoundCode: String,
    val notFoundMessage: String,
    val errorCode: String,
    val errorPrefix: String,
    val additionalNotFoundKeywords: List<String> = emptyList(),
)
