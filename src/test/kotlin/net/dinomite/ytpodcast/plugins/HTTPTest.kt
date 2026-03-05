package net.dinomite.ytpodcast.plugins

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class HTTPTest {
    @Test
    fun `XForwardedHeaders resolves real client IP from X-Forwarded-For header`() = testApplication {
        application {
            configureHTTP()
            routing {
                get("/ip") {
                    call.respondText(call.request.origin.remoteHost)
                }
            }
        }

        client.get("/ip") {
            header("X-Forwarded-For", "1.2.3.4")
        }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "1.2.3.4"
        }
    }
}
