package net.dinomite.ytpodcast.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
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
