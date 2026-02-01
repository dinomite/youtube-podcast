package net.dinomite.ytpodcast

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import net.dinomite.ytpodcast.models.ErrorResponse
import net.dinomite.ytpodcast.models.PodcastFeedRequest
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.service.AudioConverterService
import net.dinomite.ytpodcast.service.RssFeedService
import net.dinomite.ytpodcast.service.YouTubeService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplicationTest {

    @Test
    fun `test root endpoint`() = testApplication {
        application {
            testModule()
        }

        client.get("/").apply {
            assertThat(status).isEqualTo(HttpStatusCode.OK)
            assertThat(bodyAsText()).isEqualTo("YouTube to Podcast RSS Feed Converter")
        }
    }

    @Test
    fun `test health endpoint`() = testApplication {
        application {
            testModule()
        }

        client.get("/health").apply {
            assertThat(status).isEqualTo(HttpStatusCode.OK)
            assertThat(bodyAsText()).contains("healthy")
        }
    }

    @Test
    fun `test playlist feed endpoint with invalid URL`() = testApplication {
        application {
            testModule()
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        client.post("/api/playlist/feed") {
            contentType(ContentType.Application.Json)
            setBody(PodcastFeedRequest("invalid-url"))
        }.apply {
            assertThat(status).isEqualTo(HttpStatusCode.BadRequest)
            val response = Json.decodeFromString<ErrorResponse>(bodyAsText())
            assertThat(response.error).isEqualTo("invalid_request")
        }
    }

    @Test
    fun `test feed endpoint with missing playlist ID`() = testApplication {
        application {
            testModule()
        }

        client.get("/feed/").apply {
            assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `test episode endpoint with missing parameters`() = testApplication {
        application {
            testModule()
        }

        client.get("/show/test/episodes/").apply {
            assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `test 404 for unknown endpoint`() = testApplication {
        application {
            testModule()
        }

        client.get("/unknown-endpoint").apply {
            assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    private fun Application.testModule() {
        val youTubeService = YouTubeService()
        val audioConverterService = AudioConverterService()
        val rssFeedService = RssFeedService()
        val playlistController = net.dinomite.ytpodcast.controller.PlaylistController(
            youTubeService,
            rssFeedService,
            audioConverterService
        )

        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting(playlistController)
    }
}
