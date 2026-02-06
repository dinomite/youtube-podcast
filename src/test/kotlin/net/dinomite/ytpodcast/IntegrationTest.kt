package net.dinomite.ytpodcast

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.toByteArray
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.testsupport.StubYtDlpExecutor
import net.dinomite.ytpodcast.testsupport.testModuleWithStub
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IntegrationTest {
    @Nested
    inner class GetShow {
        @Test
        fun `GET show returns RSS feed for valid playlist`() = testApplication {
            val stubExecutor = StubYtDlpExecutor().apply {
                givenPlaylist(
                    "PLtest123",
                    PlaylistMetadata(
                        id = "PLtest123",
                        title = "Test Playlist",
                        description = "A test playlist for integration testing",
                        uploader = "Test Channel",
                        thumbnail = "https://example.com/thumb.jpg",
                        entries = listOf(
                            VideoMetadata(
                                id = "video1",
                                title = "First Video",
                                description = "First video description",
                                duration = 180,
                                uploadDate = "20240115",
                                uploader = "Test Channel",
                            ),
                            VideoMetadata(
                                id = "video2",
                                title = "Second Video",
                                description = "Second video description",
                                duration = 240,
                                uploadDate = "20240120",
                                uploader = "Test Channel",
                            ),
                        ),
                    ),
                )
            }

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/show/PLtest123") {
                basicAuth("testuser", "testpass")
            }.apply {
                status shouldBe HttpStatusCode.OK
                contentType()?.withoutParameters() shouldBe ContentType.Application.Rss

                val body = bodyAsText()
                body shouldContain "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                body shouldContain "<title>Test Playlist</title>"
                body shouldContain "<description>A test playlist for integration testing</description>"
                body shouldContain "<itunes:author>Test Channel</itunes:author>"
                body shouldContain "<title>First Video</title>"
                body shouldContain "<title>Second Video</title>"
                body shouldContain "/episode/video1.mp3"
                body shouldContain "/episode/video2.mp3"
                body shouldContain "<itunes:duration>03:00</itunes:duration>"
                body shouldContain "<itunes:duration>04:00</itunes:duration>"
            }
        }

        @Test
        fun `GET show returns 404 for non-existent playlist`() = testApplication {
            val stubExecutor = StubYtDlpExecutor()
            // Don't configure any playlist - it will throw "not found"

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/show/nonexistent") {
                basicAuth("testuser", "testpass")
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "not_found"
            }
        }
    }

    @Nested
    inner class GetEpisode {
        @Test
        fun `GET episode returns MP3 file for valid video`() = testApplication {
            val stubExecutor = StubYtDlpExecutor()
            val fakeAudioContent = "fake MP3 content for testing".toByteArray()
            stubExecutor.givenAudio("testvideo", fakeAudioContent)

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/episode/testvideo.mp3") {
                basicAuth("testuser", "testpass")
            }.apply {
                status shouldBe HttpStatusCode.OK
                contentType()?.withoutParameters() shouldBe ContentType.Audio.MPEG

                val responseBytes = bodyAsChannel().toByteArray()
                responseBytes shouldBe fakeAudioContent
            }
        }

        @Test
        fun `GET episode returns 404 for non-existent video`() = testApplication {
            val stubExecutor = StubYtDlpExecutor()
            // Don't configure any audio - it will throw "unavailable"

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/episode/nonexistent.mp3") {
                basicAuth("testuser", "testpass")
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "not_found"
            }
        }
    }

    @Nested
    inner class Authentication {
        @Test
        fun `GET show with valid credentials returns RSS feed`() = testApplication {
            val stubExecutor = StubYtDlpExecutor().apply {
                givenPlaylist(
                    "PLtest123",
                    PlaylistMetadata(
                        id = "PLtest123",
                        title = "Test Playlist",
                        description = "Test playlist",
                        uploader = "Test Channel",
                        thumbnail = "https://example.com/thumb.jpg",
                        entries = emptyList(),
                    ),
                )
            }

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/show/PLtest123") {
                basicAuth("testuser", "testpass")
            }.apply {
                status shouldBe HttpStatusCode.OK
                contentType()?.withoutParameters() shouldBe ContentType.Application.Rss
            }
        }

        @Test
        fun `GET show without credentials returns 401`() = testApplication {
            val stubExecutor = StubYtDlpExecutor()

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/show/PLtest123").apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `GET show with invalid credentials returns 401`() = testApplication {
            val stubExecutor = StubYtDlpExecutor()

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/show/PLtest123") {
                basicAuth("wronguser", "wrongpass")
            }.apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `GET episode with valid credentials returns audio`() = testApplication {
            val stubExecutor = StubYtDlpExecutor()
            val fakeAudioContent = "fake MP3 content".toByteArray()
            stubExecutor.givenAudio("testvideo", fakeAudioContent)

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/episode/testvideo.mp3") {
                basicAuth("testuser", "testpass")
            }.apply {
                status shouldBe HttpStatusCode.OK
                contentType()?.withoutParameters() shouldBe ContentType.Audio.MPEG
            }
        }

        @Test
        fun `GET episode without credentials returns 401`() = testApplication {
            val stubExecutor = StubYtDlpExecutor()

            application {
                testModuleWithStub(stubExecutor)
            }

            client.get("/episode/testvideo.mp3").apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

        @Test
        fun `GET health without credentials succeeds`() = testApplication {
            application {
                testModuleWithStub(StubYtDlpExecutor())
            }

            client.get("/health").apply {
                status shouldBe HttpStatusCode.OK
            }
        }

        @Test
        fun `GET root without credentials succeeds`() = testApplication {
            application {
                testModuleWithStub(StubYtDlpExecutor())
            }

            client.get("/").apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}
