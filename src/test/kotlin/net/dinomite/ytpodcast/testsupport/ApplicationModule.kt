package net.dinomite.ytpodcast.testsupport

import io.ktor.server.application.Application
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.plugins.configureAuthentication
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.CacheService
import net.dinomite.ytpodcast.services.YouTubeMetadataService

fun Application.testModuleWithStub(stubExecutor: StubYtDlpExecutor) {
    val tempDir = System.getProperty("java.io.tmpdir")
    val appConfig = AppConfig(
        baseUrl = "https://test.example.com",
        tempDir = tempDir,
        cacheDir = "$tempDir/test-cache",
        authUsername = "testuser",
        authPassword = "testpass",
    )
    val cacheConfig = CacheConfig(
        maxSize = 0L,
        maxCount = 0,
        directory = appConfig.cacheDir
    )
    val youTubeMetadataService = YouTubeMetadataService(stubExecutor)
    val audioService = AudioService(stubExecutor, appConfig.tempDir)
    val cacheService = CacheService(audioService, cacheConfig)
    cacheService.initialize()

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureAuthentication(appConfig)
    configureRouting(appConfig, youTubeMetadataService, cacheService)
}
