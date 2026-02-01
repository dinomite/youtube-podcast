package net.dinomite.ytpodcast.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class YouTubeServiceTest {
    private lateinit var youTubeService: YouTubeService

    @BeforeEach
    fun setup() {
        youTubeService = YouTubeService()
    }

    @Nested
    inner class ExtractVideoId {
        @Test
        fun `should extract ID from standard watch URL`() {
            val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            val id = youTubeService.extractVideoId(url)
            assertThat(id).isEqualTo("dQw4w9WgXcQ")
        }

        @Test
        fun `should extract ID from short URL`() {
            val url = "https://youtu.be/dQw4w9WgXcQ"
            val id = youTubeService.extractVideoId(url)
            assertThat(id).isEqualTo("dQw4w9WgXcQ")
        }

        @Test
        fun `should extract ID from embed URL`() {
            val url = "https://www.youtube.com/embed/dQw4w9WgXcQ"
            val id = youTubeService.extractVideoId(url)
            assertThat(id).isEqualTo("dQw4w9WgXcQ")
        }

        @Test
        fun `should return ID when given just the ID`() {
            val id = "dQw4w9WgXcQ"
            val result = youTubeService.extractVideoId(id)
            assertThat(result).isEqualTo(id)
        }
    }

    @Nested
    inner class GetPlaylistInfo {
        @Test
        fun `should throw exception for invalid URL`() {
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    youTubeService.getPlaylistInfo("https://example.com/invalid url with spaces")
                }
            }
        }
    }
}
