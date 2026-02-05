package net.dinomite.ytpodcast

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.util.parseSize

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val appConfig = loadAppConfig(environment.config)
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(appConfig)
}

fun loadAppConfig(config: ApplicationConfig): AppConfig = AppConfig(
    baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: "",
    tempDir = config.propertyOrNull("ytpodcast.tempDir")?.getString() ?: System.getProperty("java.io.tmpdir"),
)

fun loadCacheConfig(config: ApplicationConfig, tempDir: String): CacheConfig {
    val sizeStr = config.propertyOrNull("ytpodcast.cache.maxSize")?.getString() ?: "5GB"
    val maxSize = parseSize(sizeStr)

    val maxCount = config.propertyOrNull("ytpodcast.cache.maxCount")?.getString()?.toInt() ?: 100

    return CacheConfig(maxSize, maxCount, tempDir)
}
