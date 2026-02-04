package net.dinomite.ytpodcast.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.dinomite.ytpodcast.config.AppConfig
import org.slf4j.LoggerFactory

@Suppress("UnusedParameter")
fun Application.configureRouting(appConfig: AppConfig) {
    @Suppress("UnusedPrivateProperty")
    val logger = LoggerFactory.getLogger(Application::class.java)

    routing {
        get("/") { call.respondText("YouTube to Podcast RSS Feed Converter") }
        get("/health") { call.respond(HttpStatusCode.OK, mapOf("status" to "healthy")) }
        get("/show/{playlistId}") {
            // Get RSS feed for a playlist
        }
        get("/episode/{videoId}.mp3") {
            // Get episode audio file
        }
    }
}
