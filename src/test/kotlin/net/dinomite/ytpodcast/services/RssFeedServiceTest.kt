package net.dinomite.ytpodcast.services

import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.Thumbnail
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
        every { urlBuilder.buildEpisodeUrl("video1", any(), any(), any()) } returns
            "https://test.com/episode/video1.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain """<?xml version="1.0" encoding="UTF-8"?>"""
        rss shouldContain """xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd""""
        rss shouldContain "<title>Test Playlist</title>"
        rss shouldContain "<description>A test playlist description</description>"
        rss shouldContain "<itunes:author>Test Channel</itunes:author>"
        rss shouldContain """<itunes:image href="https://example.com/thumb.jpg"/>"""
        rss shouldContain "<image>"
        rss shouldContain "<url>https://example.com/thumb.jpg</url>"
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
            entries = listOf(VideoMetadata(id = "v1", title = "Video")),
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
            entries = listOf(VideoMetadata(id = "v1", title = "Video", uploadDate = "20240115")),
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
            entries = listOf(VideoMetadata(id = "v1", title = "Video \"with\" quotes & <tags>")),
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

    @Test
    fun `uses best thumbnail from playlist thumbnails array for show art`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test Playlist",
            thumbnails = listOf(
                Thumbnail(url = "https://example.com/small.jpg", width = 240, height = 240),
                Thumbnail(url = "https://example.com/large.jpg", width = 720, height = 720),
            ),
            entries = emptyList(),
        )

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain """<itunes:image href="https://example.com/large.jpg"/>"""
        rss shouldContain "<url>https://example.com/large.jpg</url>"
    }

    @Test
    fun `uses best thumbnail from thumbnails array for episode art`() {
        val playlist = PlaylistMetadata(
            id = "PLtest",
            title = "Test Playlist",
            entries = listOf(
                VideoMetadata(
                    id = "video1",
                    title = "First Video",
                    thumbnails = listOf(
                        Thumbnail(url = "https://example.com/small.jpg", width = 168, height = 94),
                        Thumbnail(url = "https://example.com/large.jpg", width = 336, height = 188),
                    ),
                ),
            ),
        )
        every { urlBuilder.buildEpisodeUrl("video1", any(), any(), any()) } returns
            "https://test.com/episode/video1.mp3"

        val rss = service.generateFeed(playlist, "https", "test.com", 443)

        rss shouldContain """<itunes:image href="https://example.com/large.jpg"/>"""
    }
}
