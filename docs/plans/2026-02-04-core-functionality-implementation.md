# Core Functionality Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement YouTube playlist to podcast RSS feed conversion using yt-dlp for metadata and audio extraction.

**Architecture:** Stateless Ktor service with three main services: YouTubeMetadataService (fetches playlist/video data via yt-dlp), RssFeedService (generates iTunes-compatible podcast XML), and AudioService (downloads and streams MP3 audio on-demand).

**Tech Stack:** Kotlin, Ktor 3.4+, yt-dlp CLI, kotlinx.serialization for JSON parsing, Kotlin stdlib for XML generation.

---

## Task 1: VideoMetadata Model

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/models/VideoMetadataTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/models/VideoMetadata.kt`

**Step 1: Write the failing test**

Create test file with JSON parsing test:

```kotlin
package net.dinomite.ytpodcast.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class VideoMetadataTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses yt-dlp video JSON`() {
        val ytDlpJson = """
            {
                "id": "dQw4w9WgXcQ",
                "title": "Rick Astley - Never Gonna Give You Up",
                "description": "The official video for Rick Astley",
                "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
                "duration": 212,
                "upload_date": "20091025",
                "uploader": "Rick Astley"
            }
        """.trimIndent()

        val video = json.decodeFromString<VideoMetadata>(ytDlpJson)

        video.id shouldBe "dQw4w9WgXcQ"
        video.title shouldBe "Rick Astley - Never Gonna Give You Up"
        video.description shouldBe "The official video for Rick Astley"
        video.thumbnail shouldBe "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"
        video.duration shouldBe 212
        video.uploadDate shouldBe "20091025"
        video.uploader shouldBe "Rick Astley"
    }

    @Test
    fun `handles missing optional fields`() {
        val ytDlpJson = """
            {
                "id": "abc123",
                "title": "Test Video"
            }
        """.trimIndent()

        val video = json.decodeFromString<VideoMetadata>(ytDlpJson)

        video.id shouldBe "abc123"
        video.title shouldBe "Test Video"
        video.description shouldBe null
        video.thumbnail shouldBe null
        video.duration shouldBe null
        video.uploadDate shouldBe null
        video.uploader shouldBe null
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.models.VideoMetadataTest"`
Expected: Compilation error - VideoMetadata not found

**Step 3: Write minimal implementation**

Create model file:

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    val uploader: String? = null,
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.models.VideoMetadataTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/models/VideoMetadata.kt src/test/kotlin/net/dinomite/ytpodcast/models/VideoMetadataTest.kt
git commit -m "feat: add VideoMetadata model for yt-dlp JSON parsing"
```

---

## Task 2: PlaylistMetadata Model

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadataTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadata.kt`

**Step 1: Write the failing test**

```kotlin
package net.dinomite.ytpodcast.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class PlaylistMetadataTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses yt-dlp playlist JSON`() {
        val ytDlpJson = """
            {
                "id": "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
                "title": "My Playlist",
                "description": "A collection of great videos",
                "uploader": "Channel Name",
                "thumbnail": "https://i.ytimg.com/vi/abc/default.jpg",
                "entries": [
                    {
                        "id": "video1",
                        "title": "First Video",
                        "duration": 120,
                        "upload_date": "20240101",
                        "uploader": "Channel Name"
                    },
                    {
                        "id": "video2",
                        "title": "Second Video",
                        "duration": 240,
                        "upload_date": "20240102",
                        "uploader": "Channel Name"
                    }
                ]
            }
        """.trimIndent()

        val playlist = json.decodeFromString<PlaylistMetadata>(ytDlpJson)

        playlist.id shouldBe "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        playlist.title shouldBe "My Playlist"
        playlist.description shouldBe "A collection of great videos"
        playlist.uploader shouldBe "Channel Name"
        playlist.thumbnail shouldBe "https://i.ytimg.com/vi/abc/default.jpg"
        playlist.entries.size shouldBe 2
        playlist.entries[0].id shouldBe "video1"
        playlist.entries[1].title shouldBe "Second Video"
    }

    @Test
    fun `handles empty entries`() {
        val ytDlpJson = """
            {
                "id": "PLtest",
                "title": "Empty Playlist",
                "entries": []
            }
        """.trimIndent()

        val playlist = json.decodeFromString<PlaylistMetadata>(ytDlpJson)

        playlist.entries shouldBe emptyList()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.models.PlaylistMetadataTest"`
Expected: Compilation error - PlaylistMetadata not found

**Step 3: Write minimal implementation**

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val entries: List<VideoMetadata> = emptyList(),
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.models.PlaylistMetadataTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadata.kt src/test/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadataTest.kt
git commit -m "feat: add PlaylistMetadata model for yt-dlp JSON parsing"
```

---

## Task 3: AppConfig and application.conf

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/config/AppConfig.kt`
- Create: `src/main/resources/application.conf`
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/Application.kt`

**Step 1: Create AppConfig data class**

```kotlin
package net.dinomite.ytpodcast.config

data class AppConfig(
    val baseUrl: String = "",
)
```

**Step 2: Create application.conf**

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ net.dinomite.ytpodcast.ApplicationKt.module ]
    }
}

ytpodcast {
    baseUrl = ""
    baseUrl = ${?BASE_URL}
}
```

**Step 3: Update Application.kt to load config**

Replace Application.kt contents:

```kotlin
package net.dinomite.ytpodcast

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val appConfig = loadAppConfig(environment.config)
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(appConfig)
}

fun loadAppConfig(config: ApplicationConfig): AppConfig {
    return AppConfig(
        baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: "",
    )
}
```

**Step 4: Update Routing.kt signature**

Update configureRouting to accept AppConfig (implementation stays same for now):

```kotlin
package net.dinomite.ytpodcast.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.dinomite.ytpodcast.config.AppConfig
import org.slf4j.LoggerFactory

fun Application.configureRouting(appConfig: AppConfig) {
    @Suppress("Detekt:UnusedPrivateProperty")
    val logger = LoggerFactory.getLogger(Application::class.java)

    routing {
        get("/") { call.respondText("YouTube to Podcast RSS Feed Converter") }
        get("/health") { call.respond(HttpStatusCode.OK, mapOf("status" to "healthy")) }
        get("/show/{playlistId}") {
            // Get RSS feed for a playlist
        }
        get("/episode/{videoId}.mp3") {
            // Get episode audio file
        }
    }
}
```

**Step 5: Update ApplicationTest.kt**

```kotlin
package net.dinomite.ytpodcast

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
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

    private fun Application.testModule() {
        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting(AppConfig())
    }
}
```

**Step 6: Run all tests**

Run: `./gradlew test`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/config/AppConfig.kt src/main/resources/application.conf src/main/kotlin/net/dinomite/ytpodcast/Application.kt src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt src/test/kotlin/net/dinomite/ytpodcast/ApplicationTest.kt
git commit -m "feat: add AppConfig and application.conf for base URL configuration"
```

---

## Task 4: UrlBuilder Utility

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/util/UrlBuilderTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/util/UrlBuilder.kt`

**Step 1: Write the failing test**

```kotlin
package net.dinomite.ytpodcast.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UrlBuilderTest {
    @Test
    fun `builds episode URL with configured base URL`() {
        val urlBuilder = UrlBuilder("https://podcasts.example.com")

        val url = urlBuilder.buildEpisodeUrl("dQw4w9WgXcQ")

        url shouldBe "https://podcasts.example.com/episode/dQw4w9WgXcQ.mp3"
    }

    @Test
    fun `builds episode URL from request when no config`() {
        val urlBuilder = UrlBuilder("")

        val url = urlBuilder.buildEpisodeUrl("dQw4w9WgXcQ", "https", "localhost", 8080)

        url shouldBe "https://localhost:8080/episode/dQw4w9WgXcQ.mp3"
    }

    @Test
    fun `omits port 443 for https`() {
        val urlBuilder = UrlBuilder("")

        val url = urlBuilder.buildEpisodeUrl("abc123", "https", "example.com", 443)

        url shouldBe "https://example.com/episode/abc123.mp3"
    }

    @Test
    fun `omits port 80 for http`() {
        val urlBuilder = UrlBuilder("")

        val url = urlBuilder.buildEpisodeUrl("abc123", "http", "example.com", 80)

        url shouldBe "http://example.com/episode/abc123.mp3"
    }

    @Test
    fun `config base URL takes precedence over request`() {
        val urlBuilder = UrlBuilder("https://configured.com")

        val url = urlBuilder.buildEpisodeUrl("abc123", "http", "request.com", 9000)

        url shouldBe "https://configured.com/episode/abc123.mp3"
    }

    @Test
    fun `strips trailing slash from configured base URL`() {
        val urlBuilder = UrlBuilder("https://example.com/")

        val url = urlBuilder.buildEpisodeUrl("abc123")

        url shouldBe "https://example.com/episode/abc123.mp3"
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.UrlBuilderTest"`
Expected: Compilation error - UrlBuilder not found

**Step 3: Write minimal implementation**

```kotlin
package net.dinomite.ytpodcast.util

class UrlBuilder(private val configuredBaseUrl: String) {
    fun buildEpisodeUrl(
        videoId: String,
        scheme: String = "",
        host: String = "",
        port: Int = 0,
    ): String {
        val base = resolveBaseUrl(scheme, host, port)
        return "$base/episode/$videoId.mp3"
    }

    private fun resolveBaseUrl(scheme: String, host: String, port: Int): String {
        if (configuredBaseUrl.isNotEmpty()) {
            return configuredBaseUrl.trimEnd('/')
        }

        val portSuffix = when {
            scheme == "https" && port == 443 -> ""
            scheme == "http" && port == 80 -> ""
            else -> ":$port"
        }
        return "$scheme://$host$portSuffix"
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.UrlBuilderTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/UrlBuilder.kt src/test/kotlin/net/dinomite/ytpodcast/util/UrlBuilderTest.kt
git commit -m "feat: add UrlBuilder for episode URL construction"
```

---

## Task 5: YtDlpExecutor Utility

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutorTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpException.kt`

**Step 1: Write the failing test**

```kotlin
package net.dinomite.ytpodcast.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import org.junit.jupiter.api.Test

class YtDlpExecutorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parsePlaylistJson parses valid JSON`() {
        val jsonLines = """
            {"id": "PLtest", "title": "Test Playlist", "entries": [{"id": "v1", "title": "Video 1"}]}
        """.trimIndent()

        val playlist = YtDlpExecutor.parsePlaylistJson(jsonLines, json)

        playlist.id shouldBe "PLtest"
        playlist.title shouldBe "Test Playlist"
        playlist.entries.size shouldBe 1
    }

    @Test
    fun `parseVideoJson parses valid JSON`() {
        val jsonLine = """{"id": "abc123", "title": "Test Video", "duration": 120}"""

        val video = YtDlpExecutor.parseVideoJson(jsonLine, json)

        video.id shouldBe "abc123"
        video.title shouldBe "Test Video"
        video.duration shouldBe 120
    }

    @Test
    fun `parsePlaylistJson throws on invalid JSON`() {
        val invalidJson = "not valid json"

        val exception = shouldThrow<YtDlpException> {
            YtDlpExecutor.parsePlaylistJson(invalidJson, json)
        }
        exception.message shouldContain "parse"
    }

    @Test
    fun `buildPlaylistCommand builds correct command`() {
        val command = YtDlpExecutor.buildPlaylistCommand("PLtest123")

        command shouldBe listOf(
            "yt-dlp",
            "--flat-playlist",
            "--dump-json",
            "https://www.youtube.com/playlist?list=PLtest123",
        )
    }

    @Test
    fun `buildVideoCommand builds correct command`() {
        val command = YtDlpExecutor.buildVideoCommand("dQw4w9WgXcQ")

        command shouldBe listOf(
            "yt-dlp",
            "--dump-json",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
    }

    @Test
    fun `buildDownloadCommand builds correct command`() {
        val command = YtDlpExecutor.buildDownloadCommand("dQw4w9WgXcQ", "/tmp/output.mp3")

        command shouldBe listOf(
            "yt-dlp",
            "-x",
            "--audio-format", "mp3",
            "--audio-quality", "0",
            "-o", "/tmp/output.mp3",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.YtDlpExecutorTest"`
Expected: Compilation error - YtDlpExecutor not found

**Step 3: Create YtDlpException**

```kotlin
package net.dinomite.ytpodcast.util

class YtDlpException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

**Step 4: Write YtDlpExecutor implementation**

```kotlin
package net.dinomite.ytpodcast.util

import kotlinx.serialization.json.Json
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class YtDlpExecutor(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val logger = LoggerFactory.getLogger(YtDlpExecutor::class.java)

    fun fetchPlaylist(playlistId: String): PlaylistMetadata {
        val command = buildPlaylistCommand(playlistId)
        val output = executeCommand(command)
        return parsePlaylistJson(output, json)
    }

    fun fetchVideo(videoId: String): VideoMetadata {
        val command = buildVideoCommand(videoId)
        val output = executeCommand(command)
        return parseVideoJson(output, json)
    }

    fun downloadAudio(videoId: String, outputFile: File) {
        val command = buildDownloadCommand(videoId, outputFile.absolutePath)
        executeCommand(command, timeoutMinutes = 10)
        if (!outputFile.exists()) {
            throw YtDlpException("Download completed but output file not found: ${outputFile.absolutePath}")
        }
    }

    private fun executeCommand(command: List<String>, timeoutMinutes: Long = 2): String {
        logger.debug("Executing: {}", command.joinToString(" "))
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

        if (!completed) {
            process.destroyForcibly()
            throw YtDlpException("Command timed out after $timeoutMinutes minutes")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            logger.error("yt-dlp failed with exit code {}: {}", exitCode, stderr)
            throw YtDlpException("yt-dlp failed with exit code $exitCode: $stderr")
        }

        return stdout
    }

    companion object {
        fun parsePlaylistJson(jsonText: String, json: Json): PlaylistMetadata {
            return try {
                json.decodeFromString<PlaylistMetadata>(jsonText)
            } catch (e: Exception) {
                throw YtDlpException("Failed to parse playlist JSON: ${e.message}", e)
            }
        }

        fun parseVideoJson(jsonText: String, json: Json): VideoMetadata {
            return try {
                json.decodeFromString<VideoMetadata>(jsonText)
            } catch (e: Exception) {
                throw YtDlpException("Failed to parse video JSON: ${e.message}", e)
            }
        }

        fun buildPlaylistCommand(playlistId: String): List<String> = listOf(
            "yt-dlp",
            "--flat-playlist",
            "--dump-json",
            "https://www.youtube.com/playlist?list=$playlistId",
        )

        fun buildVideoCommand(videoId: String): List<String> = listOf(
            "yt-dlp",
            "--dump-json",
            "https://www.youtube.com/watch?v=$videoId",
        )

        fun buildDownloadCommand(videoId: String, outputPath: String): List<String> = listOf(
            "yt-dlp",
            "-x",
            "--audio-format", "mp3",
            "--audio-quality", "0",
            "-o", outputPath,
            "https://www.youtube.com/watch?v=$videoId",
        )
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.YtDlpExecutorTest"`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpException.kt src/test/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutorTest.kt
git commit -m "feat: add YtDlpExecutor for yt-dlp CLI integration"
```

---

## Task 6: YouTubeMetadataService

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/services/YouTubeMetadataServiceTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/services/YouTubeMetadataService.kt`

**Step 1: Write the failing test**

```kotlin
package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.junit.jupiter.api.Test

class YouTubeMetadataServiceTest {
    private val ytDlpExecutor = mockk<YtDlpExecutor>()
    private val service = YouTubeMetadataService(ytDlpExecutor)

    @Test
    fun `getPlaylist delegates to YtDlpExecutor`() {
        val expectedPlaylist = PlaylistMetadata(
            id = "PLtest",
            title = "Test Playlist",
            entries = listOf(VideoMetadata(id = "v1", title = "Video 1")),
        )
        every { ytDlpExecutor.fetchPlaylist("PLtest") } returns expectedPlaylist

        val result = service.getPlaylist("PLtest")

        result shouldBe expectedPlaylist
        verify { ytDlpExecutor.fetchPlaylist("PLtest") }
    }

    @Test
    fun `getVideo delegates to YtDlpExecutor`() {
        val expectedVideo = VideoMetadata(id = "abc123", title = "Test Video", duration = 120)
        every { ytDlpExecutor.fetchVideo("abc123") } returns expectedVideo

        val result = service.getVideo("abc123")

        result shouldBe expectedVideo
        verify { ytDlpExecutor.fetchVideo("abc123") }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.YouTubeMetadataServiceTest"`
Expected: Compilation error - YouTubeMetadataService not found

**Step 3: Write minimal implementation**

```kotlin
package net.dinomite.ytpodcast.services

import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.YtDlpExecutor

class YouTubeMetadataService(private val ytDlpExecutor: YtDlpExecutor) {
    fun getPlaylist(playlistId: String): PlaylistMetadata {
        return ytDlpExecutor.fetchPlaylist(playlistId)
    }

    fun getVideo(videoId: String): VideoMetadata {
        return ytDlpExecutor.fetchVideo(videoId)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.YouTubeMetadataServiceTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/YouTubeMetadataService.kt src/test/kotlin/net/dinomite/ytpodcast/services/YouTubeMetadataServiceTest.kt
git commit -m "feat: add YouTubeMetadataService"
```

---

## Task 7: RssFeedService

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/services/RssFeedServiceTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/services/RssFeedService.kt`

**Step 1: Write the failing test**

```kotlin
package net.dinomite.ytpodcast.services

import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.UrlBuilder
import org.junit.jupiter.api.Test

class RssFeedServiceTest {
    private val urlBuilder = mockk<UrlBuilder>()
    private val service = RssFeedService(urlBuilder)

    @Test
    fun `generates valid RSS feed with iTunes namespace`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test Playlist",
            description = "A test playlist description",
            uploader = "Test Channel",
            thumbnail = "https://example.com/thumb.jpg",
            entries = listOf(
                VideoMetadata(
                    id = "video1",
                    title = "First Video",
                    description = "First video description",
                    thumbnail = "https://example.com/v1.jpg",
                    duration = 125,
                    uploadDate = "20240115",
                    uploader = "Test Channel",
                ),
            ),
        )
        every { urlBuilder.buildEpisodeUrl("video1", any(), any(), any()) } returns "https://test.com/episode/video1.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain """<?xml version="1.0" encoding="UTF-8"?>"""
        rss shouldContain """xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd""""
        rss shouldContain "<title>Test Playlist</title>"
        rss shouldContain "<description>A test playlist description</description>"
        rss shouldContain "<itunes:author>Test Channel</itunes:author>"
        rss shouldContain """<itunes:image href="https://example.com/thumb.jpg"/>"""
        rss shouldContain "<item>"
        rss shouldContain "<title>First Video</title>"
        rss shouldContain """<enclosure url="https://test.com/episode/video1.mp3" type="audio/mpeg" length="0"/>"""
        rss shouldContain "<guid isPermaLink=\"false\">video1</guid>"
        rss shouldContain "<itunes:duration>02:05</itunes:duration>"
    }

    @Test
    fun `handles missing optional fields`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Minimal Playlist",
            entries = listOf(
                VideoMetadata(id = "v1", title = "Video"),
            ),
        )
        every { urlBuilder.buildEpisodeUrl(any(), any(), any(), any()) } returns "https://test.com/episode/v1.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain "<title>Minimal Playlist</title>"
        rss shouldContain "<title>Video</title>"
    }

    @Test
    fun `formats duration correctly`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test",
            entries = listOf(
                VideoMetadata(id = "v1", title = "Short", duration = 45),
                VideoMetadata(id = "v2", title = "Medium", duration = 3661),
            ),
        )
        every { urlBuilder.buildEpisodeUrl(any(), any(), any(), any()) } returns "https://test.com/episode/v.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain "<itunes:duration>00:45</itunes:duration>"
        rss shouldContain "<itunes:duration>01:01:01</itunes:duration>"
    }

    @Test
    fun `converts upload date to RFC-2822 format`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test",
            entries = listOf(
                VideoMetadata(id = "v1", title = "Video", uploadDate = "20240115"),
            ),
        )
        every { urlBuilder.buildEpisodeUrl(any(), any(), any(), any()) } returns "https://test.com/episode/v1.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain "<pubDate>Mon, 15 Jan 2024 00:00:00 +0000</pubDate>"
    }

    @Test
    fun `escapes XML special characters`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test & Fun <Playlist>",
            entries = listOf(
                VideoMetadata(id = "v1", title = "Video \"with\" quotes & <tags>"),
            ),
        )
        every { urlBuilder.buildEpisodeUrl(any(), any(), any(), any()) } returns "https://test.com/episode/v1.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain "<title>Test &amp; Fun &lt;Playlist&gt;</title>"
        rss shouldContain "<title>Video &quot;with&quot; quotes &amp; &lt;tags&gt;</title>"
    }

    @Test
    fun `orders episodes by upload date newest first`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test",
            entries = listOf(
                VideoMetadata(id = "old", title = "Old Video", uploadDate = "20230101"),
                VideoMetadata(id = "new", title = "New Video", uploadDate = "20240601"),
                VideoMetadata(id = "mid", title = "Mid Video", uploadDate = "20231215"),
            ),
        )
        every { urlBuilder.buildEpisodeUrl(any(), any(), any(), any()) } returns "https://test.com/episode/v.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        val newIndex = rss.indexOf("New Video")
        val midIndex = rss.indexOf("Mid Video")
        val oldIndex = rss.indexOf("Old Video")
        assert(newIndex < midIndex) { "New video should appear before mid video" }
        assert(midIndex < oldIndex) { "Mid video should appear before old video" }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.RssFeedServiceTest"`
Expected: Compilation error - RssFeedService not found

**Step 3: Write implementation**

```kotlin
package net.dinomite.ytpodcast.services

import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.UrlBuilder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class RssFeedService(private val urlBuilder: UrlBuilder) {
    private val rfc2822Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun generateFeed(playlist: PlaylistMetadata, scheme: String, host: String, port: Int): String {
        val sortedEntries = playlist.entries.sortedByDescending { it.uploadDate ?: "" }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/">""")
            appendLine("  <channel>")
            appendLine("    <title>${escapeXml(playlist.title)}</title>")
            appendLine("    <link>https://www.youtube.com/playlist?list=${playlist.id}</link>")
            appendLine("    <description>${escapeXml(playlist.description ?: "")}</description>")
            appendLine("    <language>en</language>")
            playlist.uploader?.let { appendLine("    <itunes:author>${escapeXml(it)}</itunes:author>") }
            playlist.thumbnail?.let { appendLine("""    <itunes:image href="$it"/>""") }
            appendLine("    <itunes:explicit>false</itunes:explicit>")

            for (video in sortedEntries) {
                appendLine("    <item>")
                appendLine("      <title>${escapeXml(video.title)}</title>")
                video.description?.let { appendLine("      <description>${escapeXml(it)}</description>") }
                val episodeUrl = urlBuilder.buildEpisodeUrl(video.id, scheme, host, port)
                appendLine("""      <enclosure url="$episodeUrl" type="audio/mpeg" length="0"/>""")
                appendLine("""      <guid isPermaLink="false">${video.id}</guid>""")
                video.uploadDate?.let { appendLine("      <pubDate>${formatPubDate(it)}</pubDate>") }
                video.duration?.let { appendLine("      <itunes:duration>${formatDuration(it)}</itunes:duration>") }
                video.thumbnail?.let { appendLine("""      <itunes:image href="$it"/>""") }
                video.description?.let { appendLine("      <itunes:summary>${escapeXml(it)}</itunes:summary>") }
                appendLine("    </item>")
            }

            appendLine("  </channel>")
            append("</rss>")
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    private fun formatPubDate(uploadDate: String): String {
        val date = LocalDate.parse(uploadDate, DateTimeFormatter.BASIC_ISO_DATE)
        val dateTime = date.atStartOfDay(ZoneOffset.UTC)
        return rfc2822Formatter.format(dateTime)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.RssFeedServiceTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/RssFeedService.kt src/test/kotlin/net/dinomite/ytpodcast/services/RssFeedServiceTest.kt
git commit -m "feat: add RssFeedService for podcast XML generation"
```

---

## Task 8: AudioService

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/services/AudioServiceTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/services/AudioService.kt`

**Step 1: Write the failing test**

```kotlin
package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.junit.jupiter.api.Test
import java.io.File

class AudioServiceTest {
    private val ytDlpExecutor = mockk<YtDlpExecutor>()
    private val audioService = AudioService(ytDlpExecutor)

    @Test
    fun `downloadToTempFile calls YtDlpExecutor with temp file`() {
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadAudio("abc123", capture(fileSlot)) } answers {
            fileSlot.captured.writeText("fake mp3 content")
        }

        val result = audioService.downloadToTempFile("abc123")

        try {
            verify { ytDlpExecutor.downloadAudio("abc123", any()) }
            result.exists() shouldBe true
            result.name shouldBe "abc123.mp3"
        } finally {
            result.delete()
        }
    }

    @Test
    fun `downloadToTempFile creates file in temp directory`() {
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadAudio("xyz789", capture(fileSlot)) } answers {
            fileSlot.captured.writeText("content")
        }

        val result = audioService.downloadToTempFile("xyz789")

        try {
            result.absolutePath shouldBe fileSlot.captured.absolutePath
        } finally {
            result.delete()
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.AudioServiceTest"`
Expected: Compilation error - AudioService not found

**Step 3: Write minimal implementation**

```kotlin
package net.dinomite.ytpodcast.services

import net.dinomite.ytpodcast.util.YtDlpExecutor
import java.io.File

class AudioService(private val ytDlpExecutor: YtDlpExecutor) {
    fun downloadToTempFile(videoId: String): File {
        val tempDir = System.getProperty("java.io.tmpdir")
        val tempFile = File(tempDir, "$videoId.mp3")
        ytDlpExecutor.downloadAudio(videoId, tempFile)
        return tempFile
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.AudioServiceTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/AudioService.kt src/test/kotlin/net/dinomite/ytpodcast/services/AudioServiceTest.kt
git commit -m "feat: add AudioService for episode download"
```

---

## Task 9: Wire Up Routes

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/ApplicationTest.kt`

**Step 1: Update Routing.kt with full implementation**

```kotlin
package net.dinomite.ytpodcast.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.json.Json
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.models.ErrorResponse
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.RssFeedService
import net.dinomite.ytpodcast.services.YouTubeMetadataService
import net.dinomite.ytpodcast.util.UrlBuilder
import net.dinomite.ytpodcast.util.YtDlpException
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.slf4j.LoggerFactory

fun Application.configureRouting(appConfig: AppConfig) {
    val logger = LoggerFactory.getLogger("Routing")
    val json = Json { ignoreUnknownKeys = true }
    val ytDlpExecutor = YtDlpExecutor(json)
    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val urlBuilder = UrlBuilder(appConfig.baseUrl)
    val rssFeedService = RssFeedService(urlBuilder)
    val audioService = AudioService(ytDlpExecutor)

    routing {
        get("/") {
            call.respondText("YouTube to Podcast RSS Feed Converter")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        get("/show/{playlistId}") {
            val playlistId = call.parameters["playlistId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing playlistId"))

            try {
                val playlist = youTubeMetadataService.getPlaylist(playlistId)
                val (scheme, host, port) = requestOrigin()
                val rssFeed = rssFeedService.generateFeed(playlist, scheme, host, port)
                call.respondText(rssFeed, ContentType.Application.Rss)
            } catch (e: YtDlpException) {
                logger.error("Failed to fetch playlist $playlistId", e)
                if (e.message?.contains("unavailable", ignoreCase = true) == true ||
                    e.message?.contains("not found", ignoreCase = true) == true
                ) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Playlist not found: $playlistId"))
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("fetch_error", "Failed to fetch playlist: ${e.message}"),
                    )
                }
            }
        }

        get("/episode/{videoId}.mp3") {
            val videoId = call.parameters["videoId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Missing videoId"))

            var tempFile: java.io.File? = null
            try {
                tempFile = audioService.downloadToTempFile(videoId)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"$videoId.mp3\"",
                )
                call.respondFile(tempFile)
            } catch (e: YtDlpException) {
                logger.error("Failed to download episode $videoId", e)
                if (e.message?.contains("unavailable", ignoreCase = true) == true ||
                    e.message?.contains("not found", ignoreCase = true) == true ||
                    e.message?.contains("private", ignoreCase = true) == true
                ) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Video not found: $videoId"))
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("download_error", "Failed to download episode: ${e.message}"),
                    )
                }
            } finally {
                tempFile?.delete()
            }
        }
    }
}

private fun PipelineContext<Unit, ApplicationCall>.requestOrigin(): Triple<String, String, Int> {
    val scheme = call.request.local.scheme
    val host = call.request.host()
    val port = call.request.port()
    return Triple(scheme, host, port)
}
```

**Step 2: Add integration test for show endpoint**

Add to ApplicationTest.kt:

```kotlin
package net.dinomite.ytpodcast

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
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
            // This tests that the error handling works
            status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `test episode endpoint returns error for invalid video`() = testApplication {
        application {
            testModule()
        }

        client.get("/episode/invalid-video-id.mp3").apply {
            // Will fail because yt-dlp can't find the video
            status shouldBe HttpStatusCode.InternalServerError
        }
    }

    private fun Application.testModule() {
        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting(AppConfig())
    }
}
```

**Step 3: Run all tests**

Run: `./gradlew test`
Expected: PASS (integration tests will fail if yt-dlp not installed, which is expected)

**Step 4: Run ktlint and detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: PASS (fix any issues if needed)

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt src/test/kotlin/net/dinomite/ytpodcast/ApplicationTest.kt
git commit -m "feat: wire up show and episode routes with services"
```

---

## Task 10: Final Verification

**Step 1: Run full test suite**

Run: `./gradlew clean test`
Expected: All tests PASS

**Step 2: Run code quality checks**

Run: `./gradlew ktlintCheck detekt`
Expected: PASS

**Step 3: Manual smoke test (requires yt-dlp installed)**

Run: `./gradlew run`

In another terminal:
- `curl http://localhost:8080/` - Should return "YouTube to Podcast RSS Feed Converter"
- `curl http://localhost:8080/health` - Should return `{"status":"healthy"}`
- `curl http://localhost:8080/show/PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` - Should return RSS XML (use a real public playlist ID)

**Step 4: Final commit if any cleanup needed**

```bash
git add -A
git commit -m "chore: final cleanup and verification"
```
