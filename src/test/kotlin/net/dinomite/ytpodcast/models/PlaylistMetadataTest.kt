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
}
