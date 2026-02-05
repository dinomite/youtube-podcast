package net.dinomite.ytpodcast.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class YtDlpExecutorTest {
    private val json = YtDlpExecutor.jsonParser

    @Nested
    inner class ParsePlaylistJson {
        @Test
        fun `parsePlaylistJson throws on invalid JSON`() {
            val invalidJson = "not valid json"

            val exception = shouldThrow<YtDlpException> {
                YtDlpExecutor.parsePlaylistJson(invalidJson, json)
            }
            exception.message shouldContain "parse"
        }

        @Test
        @Suppress("ktlint:standard:indent")
        fun `parsePlaylistJson parses valid NDJSON`() {
            val ndjson = """
            {"id": "v1", "title": "Video 1", "playlist_id": "PLtest", "playlist_title": "Test Playlist", "playlist_uploader": "Test Channel"}
            {"id": "v2", "title": "Video 2", "playlist_id": "PLtest", "playlist_title": "Test Playlist", "playlist_uploader": "Test Channel"}
        """.trimIndent()

            val playlist = YtDlpExecutor.parsePlaylistJson(ndjson, json)

            playlist.id shouldBe "PLtest"
            playlist.title shouldBe "Test Playlist"
            playlist.uploader shouldBe "Test Channel"
            playlist.entries.size shouldBe 2
            playlist.entries[0].id shouldBe "v1"
            playlist.entries[1].id shouldBe "v2"
        }

        @Test
        fun `parsePlaylistJson parses NDJSON from real yt-dlp output`() {
            val ndjson = this::class.java.getResource("/test-playlist.json")!!.readText()

            val playlist = YtDlpExecutor.parsePlaylistJson(ndjson, json)

            playlist.id shouldBe "PLQlnTldJs0ZSINUQoJ2lY-z1HKO-XUWP8"
            playlist.title shouldBe "Greeking Out Podcast Season 12 | Nat Geo Kids"
            playlist.uploader shouldBe "Nat Geo Kids"
            playlist.entries.size shouldBe 11
            playlist.entries[0].id shouldBe "gUIStb1aVxg"
            playlist.entries[0].title shouldContain "Percy Jackson"
            playlist.entries[10].id shouldBe "P0agJv2JAVM"
            playlist.entries[10].title shouldContain "Snake Stories"
        }
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
