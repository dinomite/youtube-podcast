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
