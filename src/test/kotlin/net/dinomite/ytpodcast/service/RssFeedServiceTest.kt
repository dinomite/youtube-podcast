package net.dinomite.ytpodcast.service

import kotlinx.coroutines.runBlocking
import net.dinomite.ytpodcast.models.PlaylistInfo
import net.dinomite.ytpodcast.models.ResponseVideoInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RssFeedServiceTest {
    private lateinit var rssFeedService: RssFeedService

    @BeforeEach
    fun setup() {
        rssFeedService = RssFeedService()
    }

    @Test
    fun `generatePodcastFeed should create valid RSS feed`() = runBlocking {
        val playlist = PlaylistInfo(
            id = "test-playlist-id",
            title = "Test Playlist",
            description = "Test Description",
            author = "Test Author",
            videos = listOf(
                ResponseVideoInfo(
                    id = "video1",
                    title = "Episode 1",
                    description = "First episode",
                    author = "Author 1",
                    duration = 300,
                    publishedAt = System.currentTimeMillis() / 1000,
                    thumbnailUrl = "https://example.com/video1.jpg"
                ),
                ResponseVideoInfo(
                    id = "video2",
                    title = "Episode 2",
                    description = "Second episode",
                    author = "Author 2",
                    duration = 600,
                    publishedAt = System.currentTimeMillis() / 1000,
                    thumbnailUrl = "https://example.com/video2.jpg"
                )
            )
        )

        val feed = rssFeedService.generatePodcastFeed(playlist, "https://example.com")

        assertThat(feed).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        assertThat(feed).contains("<rss")
        assertThat(feed).contains("Test Playlist")
        assertThat(feed).contains("Test Description")
        assertThat(feed).contains("Episode 1")
        assertThat(feed).contains("Episode 2")
        assertThat(feed).contains("https://example.com/show/test-playlist-id/episodes/video1.mp3")
        assertThat(feed).contains("https://example.com/show/test-playlist-id/episodes/video2.mp3")
        assertThat(feed).contains("<enclosure")
        assertThat(feed).contains("audio/mpeg")
    }

    @Test
    fun `generatePodcastFeed should handle empty playlist`() = runBlocking {
        val playlist = PlaylistInfo(
            id = "empty-playlist",
            title = "Empty Playlist",
            description = null,
            author = null,
            videos = emptyList()
        )

        val feed = rssFeedService.generatePodcastFeed(playlist, "https://example.com")

        assertThat(feed).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        assertThat(feed).contains("<rss")
        assertThat(feed).contains("Empty Playlist")
        assertThat(feed).doesNotContain("<item>")
    }

    @Test
    fun `generatePodcastFeed should handle videos with missing data`() = runBlocking {
        val playlist = PlaylistInfo(
            id = "test-playlist",
            title = "Test Playlist",
            description = null,
            author = null,
            videos = listOf(
                ResponseVideoInfo(
                    id = "video1",
                    title = "Episode with minimal data",
                    description = null,
                    author = null,
                    duration = null,
                    publishedAt = null,
                    thumbnailUrl = null
                )
            )
        )

        val feed = rssFeedService.generatePodcastFeed(playlist, "https://example.com")

        assertThat(feed).contains("Episode with minimal data")
        assertThat(feed).contains("https://example.com/show/test-playlist/episodes/video1.mp3")
    }
}
