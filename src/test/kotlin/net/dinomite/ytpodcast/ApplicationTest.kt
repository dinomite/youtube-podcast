package net.dinomite.ytpodcast

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.CacheService
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.junit.jupiter.api.Test

class ApplicationTest {
    @Test
    fun `test root endpoint`() = testApplication {
        application {
            testModule()
        }

        client.get("/").apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "YouTube to Podcast RSS Feed Converter"
        }
    }

    @Test
    fun `test health endpoint`() = testApplication {
        application {
            testModule()
        }

        client.get("/health").apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldContain "healthy"
        }
    }

    @Test
    fun `test 404 for unknown endpoint`() = testApplication {
        application {
            testModule()
        }

        client.get("/unknown-endpoint").apply {
            status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `test show endpoint returns error for invalid playlist`() = testApplication {
        application {
            testModule()
        }

        client.get("/show/invalid-playlist-id").apply {
            // Will fail because yt-dlp can't find the playlist
            // Returns 404 if error contains "not found", otherwise 500
            status.value shouldBeGreaterThanOrEqual 400
        }
    }

    @Test
    fun `test episode endpoint returns error for invalid video`() = testApplication {
        application {
            testModule()
        }

        client.get("/episode/invalid-video-id.mp3").apply {
            // Will fail because yt-dlp can't find the video
            // Returns 404 if error contains "not found", otherwise 500
            status.value shouldBeGreaterThanOrEqual 400
        }
    }

    private fun Application.testModule() {
        val appConfig = AppConfig(
            baseUrl = "",
            tempDir = System.getProperty("java.io.tmpdir")
        )
        val cacheConfig = CacheConfig(
            maxSize = 0L,
            maxCount = 0,
            directory = appConfig.tempDir
        )
        val ytDlpExecutor = YtDlpExecutor()
        val audioService = AudioService(ytDlpExecutor, cacheConfig.directory)
        val cacheService = CacheService(audioService, cacheConfig)
        cacheService.initialize()

        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting(appConfig, cacheService)
    }
}
