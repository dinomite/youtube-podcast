package net.dinomite.ytpodcast

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.CacheService
import net.dinomite.ytpodcast.services.YouTubeMetadataService
import net.dinomite.ytpodcast.util.YtDlpExecutor

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val appConfig = AppConfig.load(environment.config)
    val cacheConfig = CacheConfig(environment.config, appConfig.cacheDir)

    val ytDlpExecutor = YtDlpExecutor()
    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val audioService = AudioService(ytDlpExecutor, appConfig.tempDir)
    val cacheService = CacheService(audioService, cacheConfig)

    cacheService.initialize()

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(appConfig, youTubeMetadataService, cacheService)
}
