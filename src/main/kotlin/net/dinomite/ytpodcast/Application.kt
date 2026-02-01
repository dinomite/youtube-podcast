package net.dinomite.ytpodcast

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.dinomite.ytpodcast.controller.PlaylistController
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.service.AudioConverterService
import net.dinomite.ytpodcast.service.RssFeedService
import net.dinomite.ytpodcast.service.YouTubeService

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // Initialize services
    val youTubeService = YouTubeService()
    val audioConverterService = AudioConverterService()
    val rssFeedService = RssFeedService()
    val playlistController = PlaylistController(
        youTubeService,
        rssFeedService,
        audioConverterService
    )

    // Configure plugins
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(playlistController)
}
