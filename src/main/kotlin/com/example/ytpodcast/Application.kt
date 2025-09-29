package com.example.ytpodcast

import com.example.ytpodcast.plugins.configureHTTP
import com.example.ytpodcast.plugins.configureMonitoring
import com.example.ytpodcast.plugins.configureRouting
import com.example.ytpodcast.plugins.configureSerialization
import com.example.ytpodcast.service.AudioConverterService
import com.example.ytpodcast.service.RssFeedService
import com.example.ytpodcast.service.YouTubeService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

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
    
    // Configure plugins
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(youTubeService, audioConverterService, rssFeedService)
}