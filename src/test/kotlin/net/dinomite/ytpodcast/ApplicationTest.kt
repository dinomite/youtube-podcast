package net.dinomite.ytpodcast

import com.typesafe.config.ConfigFactory
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.testApplication
import java.io.InputStreamReader
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

        client.get("/show/invalid-playlist-id") {
            basicAuth("testuser", "testpass")
        }.apply {
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

        client.get("/episode/invalid-video-id.mp3") {
            basicAuth("testuser", "testpass")
        }.apply {
            // Will fail because yt-dlp can't find the video
            // Returns 404 if error contains "not found", otherwise 500
            status.value shouldBeGreaterThanOrEqual 400
        }
    }

    @Test
    fun `module loads with config from file`() = testApplication {
        environment {
            config = HoconApplicationConfig(
                ConfigFactory.parseReader(
                    InputStreamReader(
                        javaClass.classLoader.getResourceAsStream("test-application-minimal.conf")
                            ?: error("test-application-minimal.conf not found")
                    )
                )
            )
        }

        application {
            module()
        }

        client.get("/").apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "YouTube to Podcast RSS Feed Converter"
        }
    }

    private fun Application.testModule() {
        val tempDir = System.getProperty("java.io.tmpdir")
        val appConfig = AppConfig(
            baseUrl = "",
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
        val ytDlpExecutor = YtDlpExecutor()
        val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
        val audioService = AudioService(ytDlpExecutor, appConfig.tempDir)
        val cacheService = CacheService(audioService, cacheConfig)
        cacheService.initialize()

        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureAuthentication(appConfig)
        configureRouting(appConfig, youTubeMetadataService, cacheService)
    }
}
