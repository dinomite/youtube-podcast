package net.dinomite.ytpodcast.models

import io.kotest.matchers.shouldBe
import net.dinomite.ytpodcast.models.Thumbnail
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.junit.jupiter.api.Test

class VideoMetadataTest {
    private val json = YtDlpExecutor.jsonParser

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

    @Test
    fun `bestThumbnail returns highest resolution thumbnail URL`() {
        val video = VideoMetadata(
            id = "test",
            title = "Test",
            thumbnails = listOf(
                Thumbnail(url = "https://example.com/small.jpg", width = 168, height = 94),
                Thumbnail(url = "https://example.com/large.jpg", width = 336, height = 188),
                Thumbnail(url = "https://example.com/medium.jpg", width = 246, height = 138),
            ),
        )

        video.bestThumbnail shouldBe "https://example.com/large.jpg"
    }

    @Test
    fun `bestThumbnail falls back to thumbnail field when thumbnails is empty`() {
        val video = VideoMetadata(id = "test", title = "Test", thumbnail = "https://example.com/fallback.jpg")

        video.bestThumbnail shouldBe "https://example.com/fallback.jpg"
    }

    @Test
    fun `bestThumbnail returns null when both thumbnails and thumbnail are absent`() {
        val video = VideoMetadata(id = "test", title = "Test")

        video.bestThumbnail shouldBe null
    }
}
