package net.dinomite.ytpodcast.plugins

import io.kotest.matchers.shouldBe
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.dinomite.ytpodcast.config.AppConfig
import org.junit.jupiter.api.Test

class AuthenticationTest {
    @Test
    fun `authenticated route with valid credentials returns 200`() = testApplication {
        application {
            testAuthModule()
        }

        client.get("/protected") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "protected content"
        }
    }

    @Test
    fun `authenticated route without credentials returns 401`() = testApplication {
        application {
            testAuthModule()
        }

        client.get("/protected").apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `authenticated route with invalid username returns 401`() = testApplication {
        application {
            testAuthModule()
        }

        client.get("/protected") {
            basicAuth("wronguser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `authenticated route with invalid password returns 401`() = testApplication {
        application {
            testAuthModule()
        }

        client.get("/protected") {
            basicAuth("testuser", "wrongpass")
        }.apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }

    private fun Application.testAuthModule() {
        val appConfig = AppConfig(
            baseUrl = "",
            tempDir = "",
            cacheDir = "",
            authUsername = "testuser",
            authPassword = "testpass",
        )

        configureAuthentication(appConfig)

        routing {
            authenticate("podcast-auth") {
                get("/protected") {
                    call.respondText("protected content")
                }
            }
        }
    }
}
