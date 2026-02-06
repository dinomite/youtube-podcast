package net.dinomite.ytpodcast

import com.typesafe.config.ConfigFactory
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.testApplication
import java.io.InputStreamReader
import net.dinomite.ytpodcast.testsupport.StubYtDlpExecutor
import net.dinomite.ytpodcast.testsupport.testModuleWithStub
import org.junit.jupiter.api.Test

class ApplicationTest {
    @Test
    fun `test root endpoint`() = testApplication {
        application {
            testModuleWithStub(StubYtDlpExecutor())
        }

        client.get("/").apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "YouTube to Podcast RSS Feed Converter"
        }
    }

    @Test
    fun `test health endpoint`() = testApplication {
        application {
            testModuleWithStub(StubYtDlpExecutor())
        }

        client.get("/health").apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldContain "healthy"
        }
    }

    @Test
    fun `test 404 for unknown endpoint`() = testApplication {
        application {
            testModuleWithStub(StubYtDlpExecutor())
        }

        client.get("/unknown-endpoint").apply {
            status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `test show endpoint returns error for invalid playlist`() = testApplication {
        application {
            testModuleWithStub(StubYtDlpExecutor())
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
            testModuleWithStub(StubYtDlpExecutor())
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
}
