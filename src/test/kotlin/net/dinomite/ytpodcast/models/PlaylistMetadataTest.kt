package net.dinomite.ytpodcast.models

import io.kotest.matchers.shouldBe
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.junit.jupiter.api.Test

class PlaylistMetadataTest {
    private val json = YtDlpExecutor.jsonParser

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

    @Test
    fun `bestThumbnail returns highest resolution thumbnail URL`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test",
            thumbnails = listOf(
                Thumbnail(url = "https://example.com/small.jpg", width = 240, height = 240),
                Thumbnail(url = "https://example.com/large.jpg", width = 720, height = 720),
                Thumbnail(url = "https://example.com/medium.jpg", width = 480, height = 480),
            ),
        )

        playlist.bestThumbnail shouldBe "https://example.com/large.jpg"
    }

    @Test
    fun `bestThumbnail falls back to thumbnail field when thumbnails is empty`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test",
            thumbnail = "https://example.com/fallback.jpg",
        )

        playlist.bestThumbnail shouldBe "https://example.com/fallback.jpg"
    }

    @Test
    fun `bestThumbnail returns null when both thumbnails and thumbnail are absent`() {
        val playlist = PlaylistMetadata(id = "PLtest", title = "Test")

        playlist.bestThumbnail shouldBe null
    }

    @Test
    fun `parses thumbnails array from yt-dlp playlist JSON`() {
        val ytDlpJson = """
            {
                "id": "PLtest",
                "title": "Test Playlist",
                "thumbnails": [
                    {"url": "https://example.com/240.jpg", "height": 240, "width": 240, "id": "0", "resolution": "240x240"},
                    {"url": "https://example.com/720.jpg", "height": 720, "width": 720, "id": "2", "resolution": "720x720"},
                    {"url": "https://example.com/480.jpg", "height": 480, "width": 480, "id": "1", "resolution": "480x480"}
                ],
                "entries": []
            }
        """.trimIndent()

        val playlist = json.decodeFromString<PlaylistMetadata>(ytDlpJson)

        playlist.thumbnails.size shouldBe 3
        playlist.bestThumbnail shouldBe "https://example.com/720.jpg"
    }
}
