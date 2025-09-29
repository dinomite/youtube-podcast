// File: src/test/kotlin/com/example/ytpodcast/service/YouTubeServiceTest.kt
package com.example.ytpodcast.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class YouTubeServiceTest {
    private lateinit var youTubeService: YouTubeService
    
    @BeforeEach
    fun setup() {
        youTubeService = YouTubeService()
    }
    
    @Test
    fun `extractPlaylistId should extract ID from standard URL`() {
        val url = "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        val id = youTubeService.extractPlaylistId(url)
        assertThat(id).isEqualTo("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
    }
    
    @Test
    fun `extractPlaylistId should extract ID from watch URL with list parameter`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        val id = youTubeService.extractPlaylistId(url)
        assertThat(id).isEqualTo("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
    }
    
    @Test
    fun `extractPlaylistId should return ID when given just the ID`() {
        val id = "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        val result = youTubeService.extractPlaylistId(id)
        assertThat(result).isEqualTo(id)
    }
    
    @Test
    fun `extractPlaylistId should return null for invalid URL`() {
        val url = "https://example.com/not-a-youtube-url"
        val id = youTubeService.extractPlaylistId(url)
        assertThat(id).isNull()
    }
    
    @Test
    fun `extractVideoId should extract ID from standard watch URL`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val id = youTubeService.extractVideoId(url)
        assertThat(id).isEqualTo("dQw4w9WgXcQ")
    }
    
    @Test
    fun `extractVideoId should extract ID from short URL`() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val id = youTubeService.extractVideoId(url)
        assertThat(id).isEqualTo("dQw4w9WgXcQ")
    }
    
    @Test
    fun `extractVideoId should extract ID from embed URL`() {
        val url = "https://www.youtube.com/embed/dQw4w9WgXcQ"
        val id = youTubeService.extractVideoId(url)
        assertThat(id).isEqualTo("dQw4w9WgXcQ")
    }
    
    @Test
    fun `extractVideoId should return ID when given just the ID`() {
        val id = "dQw4w9WgXcQ"
        val result = youTubeService.extractVideoId(id)
        assertThat(result).isEqualTo(id)
    }
    
    @Test
    fun `getPlaylistInfo should throw exception for invalid URL`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                youTubeService.getPlaylistInfo("not-a-valid-url")
            }
        }
    }
}

// File: src/test/kotlin/com/example/ytpodcast/service/AudioConverterServiceTest.kt
package com.example.ytpodcast.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioConverterServiceTest {
    private lateinit var audioConverterService: AudioConverterService
    
    @BeforeEach
    fun setup() {
        audioConverterService = AudioConverterService()
    }
    
    @Test
    fun `isValidAudioFormat should detect MP3 with ID3 tag`() = runBlocking {
        val mp3Data = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x03, 0x00, 0x00, 0x00, 0x00
        )
        
        val isValid = audioConverterService.isValidAudioFormat(mp3Data)
        assertThat(isValid).isTrue()
    }
    
    @Test
    fun `isValidAudioFormat should detect MP3 with MPEG header`() = runBlocking {
        val mp3Data = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00
        )
        
        val isValid = audioConverterService.isValidAudioFormat(mp3Data)
        assertThat(isValid).isTrue()
    }
    
    @Test
    fun `isValidAudioFormat should detect MP4 format`() = runBlocking {
        val mp4Data = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()
        )
        
        val isValid = audioConverterService.isValidAudioFormat(mp4Data)
        assertThat(isValid).isTrue()
    }
    
    @Test
    fun `isValidAudioFormat should detect WebM format`() = runBlocking {
        val webmData = byteArrayOf(
            0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(),
            0x00, 0x00, 0x00, 0x00
        )
        
        val isValid = audioConverterService.isValidAudioFormat(webmData)
        assertThat(isValid).isTrue()
    }
    
    @Test
    fun `isValidAudioFormat should detect OGG format`() = runBlocking {
        val oggData = byteArrayOf(
            'O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(),
            0x00, 0x02, 0x00, 0x00
        )
        
        val isValid = audioConverterService.isValidAudioFormat(oggData)
        assertThat(isValid).isTrue()
    }
    
    @Test
    fun `isValidAudioFormat should return false for invalid format`() = runBlocking {
        val invalidData = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        
        val isValid = audioConverterService.isValidAudioFormat(invalidData)
        assertThat(isValid).isFalse()
    }
    
    @Test
    fun `isValidAudioFormat should handle empty array`() = runBlocking {
        val emptyData = byteArrayOf()
        
        val isValid = audioConverterService.isValidAudioFormat(emptyData)
        assertThat(isValid).isFalse()
    }
}

// File: src/test/kotlin/com/example/ytpodcast/service/RssFeedServiceTest.kt
package com.example.ytpodcast.service

import com.example.ytpodcast.models.PlaylistInfo
import com.example.ytpodcast.models.VideoInfo
import kotlinx.coroutines.runBlocking
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
            thumbnailUrl = "https://example.com/thumbnail.jpg",
            videos = listOf(
                VideoInfo(
                    id = "video1",
                    title = "Episode 1",
                    description = "First episode",
                    author = "Author 1",
                    duration = 300,
                    publishedAt = System.currentTimeMillis() / 1000,
                    thumbnailUrl = "https://example.com/video1.jpg"
                ),
                VideoInfo(
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
            thumbnailUrl = null,
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
            thumbnailUrl = null,
            videos = listOf(
                VideoInfo(
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

// File: src/test/kotlin/com/example/ytpodcast/ApplicationTest.kt
package com.example.ytpodcast

import com.example.ytpodcast.models.ErrorResponse
import com.example.ytpodcast.models.PodcastFeedRequest
import com.example.ytpodcast.plugins.*
import com.example.ytpodcast.service.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplicationTest {
    
    @Test
    fun `test root endpoint`() = testApplication {
        application {
            testModule()
        }
        
        client.get("/").apply {
            assertThat(status).isEqualTo(HttpStatusCode.OK)
            assertThat(bodyAsText()).isEqualTo("YouTube to Podcast RSS Feed Converter")
        }
    }
    
    @Test
    fun `test health endpoint`() = testApplication {
        application {
            testModule()
        }
        
        client.get("/health").apply {
            assertThat(status).isEqualTo(HttpStatusCode.OK)
            assertThat(bodyAsText()).contains("healthy")
        }
    }
    
    @Test
    fun `test playlist feed endpoint with invalid URL`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        
        client.post("/api/playlist/feed") {
            contentType(ContentType.Application.Json)
            setBody(PodcastFeedRequest("invalid-url"))
        }.apply {
            assertThat(status).isEqualTo(HttpStatusCode.BadRequest)
            val response = Json.decodeFromString<ErrorResponse>(bodyAsText())
            assertThat(response.error).isEqualTo("invalid_request")
        }
    }
    
    @Test
    fun `test feed endpoint with missing playlist ID`() = testApplication {
        application {
            testModule()
        }
        
        client.get("/feed/").apply {
            assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        }
    }
    
    @Test
    fun `test episode endpoint with missing parameters`() = testApplication {
        application {
            testModule()
        }
        
        client.get("/show/test/episodes/").apply {
            assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        }
    }
    
    @Test
    fun `test 404 for unknown endpoint`() = testApplication {
        application {
            testModule()
        }
        
        client.get("/unknown-endpoint").apply {
            assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        }
    }
    
    private fun Application.testModule() {
        val youTubeService = YouTubeService()
        val audioConverterService = AudioConverterService()
        val rssFeedService = RssFeedService()
        
        configureSerialization()
        configureMonitoring()
        configureHTTP()
        configureRouting(youTubeService, audioConverterService, rssFeedService)
    }
}