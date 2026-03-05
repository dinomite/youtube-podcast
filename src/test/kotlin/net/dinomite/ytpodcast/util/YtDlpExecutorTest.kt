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
            val exception = shouldThrow<YtDlpException> {
                YtDlpExecutor.parsePlaylistJson("not valid json", json)
            }
            exception.message shouldContain "parse"
        }

        @Test
        fun `parsePlaylistJson parses valid single JSON`() {
            val singleJson = """
                {
                    "id": "PLtest",
                    "title": "Test Playlist",
                    "uploader": "Test Channel",
                    "thumbnail": "https://example.com/playlist-thumb.jpg",
                    "entries": [
                        {"id": "v1", "title": "Video 1"},
                        {"id": "v2", "title": "Video 2"}
                    ]
                }
            """.trimIndent()

            val playlist = YtDlpExecutor.parsePlaylistJson(singleJson, json)

            playlist.id shouldBe "PLtest"
            playlist.title shouldBe "Test Playlist"
            playlist.uploader shouldBe "Test Channel"
            playlist.thumbnail shouldBe "https://example.com/playlist-thumb.jpg"
            playlist.entries.size shouldBe 2
            playlist.entries[0].id shouldBe "v1"
            playlist.entries[1].id shouldBe "v2"
        }

        @Test
        fun `parsePlaylistJson parses single JSON from real yt-dlp output`() {
            val singleJson = this::class.java.getResource("/test-playlist.json")!!.readText()

            val playlist = YtDlpExecutor.parsePlaylistJson(singleJson, json)

            playlist.id shouldBe "PLQlnTldJs0ZSINUQoJ2lY-z1HKO-XUWP8"
            playlist.title shouldBe "Greeking Out Podcast Season 12 | Nat Geo Kids"
            playlist.uploader shouldBe "Nat Geo Kids"
            playlist.thumbnail shouldBe "https://i.ytimg.com/vi/gUIStb1aVxg/hqdefault.jpg"
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
            "--dump-single-json",
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
    fun `buildRawDownloadCommand builds correct command`() {
        val command = YtDlpExecutor.buildRawDownloadCommand("dQw4w9WgXcQ", "/tmp/output")

        command shouldBe listOf(
            "yt-dlp",
            "-f",
            "bestaudio/best",
            "-o",
            "/tmp/output",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
    }
}
