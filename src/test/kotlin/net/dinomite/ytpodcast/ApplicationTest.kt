package net.dinomite.ytpodcast

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
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
    fun `test 404 for unknown endpoint`() = testApplication {
        application {
            testModule()
        }

        client.get("/unknown-endpoint").apply {
            assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    private fun Application.testModule() {
        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting()
    }
}
