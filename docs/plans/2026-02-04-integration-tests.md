# Integration Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create end-to-end integration tests that test the full HTTP request/response cycle with a stubbed YtDlpExecutor.

**Architecture:** Make YtDlpExecutor extensible (open class with open methods), create a StubYtDlpExecutor for tests, add an overloaded configureRouting that accepts a YtDlpExecutor, then write integration tests that verify RSS feed generation and audio streaming.

**Tech Stack:** Kotlin, Ktor testApplication, kotest matchers, existing project test infrastructure.

---

## Task 1: Make YtDlpExecutor Extensible

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt`

**Step 1: Add `open` modifier to class and methods**

Change the class declaration and the three public methods to be `open`:

```kotlin
open class YtDlpExecutor(private val json: Json = Json { ignoreUnknownKeys = true },) {
    // ...

    open fun fetchPlaylist(playlistId: String): PlaylistMetadata {
        // existing implementation
    }

    open fun fetchVideo(videoId: String): VideoMetadata {
        // existing implementation
    }

    open fun downloadAudio(videoId: String, outputFile: File) {
        // existing implementation
    }
}
```

**Step 2: Run existing tests to verify no regression**

Run: `./gradlew test`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt
git commit -m "refactor: make YtDlpExecutor extensible for testing"
```

---

## Task 2: Add Overloaded configureRouting

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`

**Step 1: Extract service creation into a helper and add overload**

Add a new overloaded function that accepts YtDlpExecutor. Refactor so the original function calls the new one:

```kotlin
fun Application.configureRouting(appConfig: AppConfig) {
    val json = Json { ignoreUnknownKeys = true }
    val ytDlpExecutor = YtDlpExecutor(json)
    configureRouting(appConfig, ytDlpExecutor)
}

fun Application.configureRouting(appConfig: AppConfig, ytDlpExecutor: YtDlpExecutor) {
    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val urlBuilder = UrlBuilder(appConfig.baseUrl)
    val rssFeedService = RssFeedService(urlBuilder)
    val audioService = AudioService(ytDlpExecutor)

    val handlers = RouteHandlers(youTubeMetadataService, rssFeedService, audioService)

    routing {
        get("/") {
            call.respondText("YouTube to Podcast RSS Feed Converter")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        handlers.registerShowRoute(this)
        handlers.registerEpisodeRoute(this)
    }
}
```

**Step 2: Run existing tests to verify no regression**

Run: `./gradlew test`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt
git commit -m "refactor: add configureRouting overload accepting YtDlpExecutor"
```

---

## Task 3: Create StubYtDlpExecutor

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/testing/StubYtDlpExecutor.kt`

**Step 1: Create the stub class**

```kotlin
package net.dinomite.ytpodcast.testsupport

import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.YtDlpException
import net.dinomite.ytpodcast.util.YtDlpExecutor
import java.io.File

class StubYtDlpExecutor : YtDlpExecutor() {
    private val playlists = mutableMapOf<String, PlaylistMetadata>()
    private val videos = mutableMapOf<String, VideoMetadata>()
    private val audioContent = mutableMapOf<String, ByteArray>()

    fun givenPlaylist(playlistId: String, metadata: PlaylistMetadata) {
        playlists[playlistId] = metadata
    }

    fun givenVideo(videoId: String, metadata: VideoMetadata) {
        videos[videoId] = metadata
    }

    fun givenAudio(videoId: String, content: ByteArray) {
        audioContent[videoId] = content
    }

    override fun fetchPlaylist(playlistId: String): PlaylistMetadata {
        return playlists[playlistId]
            ?: throw YtDlpException("yt-dlp failed with exit code 1: Playlist not found: $playlistId")
    }

    override fun fetchVideo(videoId: String): VideoMetadata {
        return videos[videoId]
            ?: throw YtDlpException("yt-dlp failed with exit code 1: Video not found: $videoId")
    }

    override fun downloadAudio(videoId: String, outputFile: File) {
        val content = audioContent[videoId]
            ?: throw YtDlpException("yt-dlp failed with exit code 1: Video unavailable: $videoId")
        outputFile.writeBytes(content)
    }
}
```

**Step 2: Run ktlint to check formatting**

Run: `./gradlew ktlintCheck`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/kotlin/net/dinomite/ytpodcast/testing/StubYtDlpExecutor.kt
git commit -m "test: add StubYtDlpExecutor for integration testing"
```

---

## Task 4: Write Integration Test for /show Endpoint

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt`

**Step 1: Write the integration test for RSS feed generation**

```kotlin
package net.dinomite.ytpodcast

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.testsupport.StubYtDlpExecutor
import org.junit.jupiter.api.Test

class IntegrationTest {
    @Test
    fun `GET show returns RSS feed for valid playlist`() = testApplication {
        val stubExecutor = StubYtDlpExecutor()
        stubExecutor.givenPlaylist(
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

        application {
            testModuleWithStub(stubExecutor)
        }

        client.get("/show/PLtest123").apply {
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

        client.get("/show/nonexistent").apply {
            status shouldBe HttpStatusCode.NotFound
            bodyAsText() shouldContain "not_found"
        }
    }

    private fun Application.testModuleWithStub(stubExecutor: StubYtDlpExecutor) {
        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting(AppConfig(baseUrl = "https://test.example.com"), stubExecutor)
    }
}
```

**Step 2: Run the test**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest"`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt
git commit -m "test: add integration test for /show endpoint"
```

---

## Task 5: Add Integration Test for /episode Endpoint

**Files:**
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt`

**Step 1: Add tests for the episode endpoint**

Add these tests to the existing IntegrationTest class:

```kotlin
@Test
fun `GET episode returns MP3 file for valid video`() = testApplication {
    val stubExecutor = StubYtDlpExecutor()
    val fakeAudioContent = "fake MP3 content for testing".toByteArray()
    stubExecutor.givenAudio("testvideo", fakeAudioContent)

    application {
        testModuleWithStub(stubExecutor)
    }

    client.get("/episode/testvideo.mp3").apply {
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

    client.get("/episode/nonexistent.mp3").apply {
        status shouldBe HttpStatusCode.NotFound
        bodyAsText() shouldContain "not_found"
    }
}
```

**Step 2: Add required import**

Add this import at the top of the file:

```kotlin
import io.ktor.utils.io.toByteArray
```

**Step 3: Run all integration tests**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest"`
Expected: PASS (4 tests)

**Step 4: Commit**

```bash
git add src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt
git commit -m "test: add integration tests for /episode endpoint"
```

---

## Task 6: Final Verification

**Step 1: Run full test suite**

Run: `./gradlew clean test`
Expected: All tests PASS

**Step 2: Run code quality checks**

Run: `./gradlew ktlintCheck detekt`
Expected: PASS

**Step 3: Verify test count increased**

The test output should show approximately 35 tests (31 existing + 4 new integration tests).

**Step 4: Commit if any cleanup needed**

```bash
git add -A
git commit -m "chore: final cleanup for integration tests"
```
