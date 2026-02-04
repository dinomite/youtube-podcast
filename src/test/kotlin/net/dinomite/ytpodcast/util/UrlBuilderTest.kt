package net.dinomite.ytpodcast.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UrlBuilderTest {
    @Test
    fun `builds episode URL with configured base URL`() {
        val urlBuilder = UrlBuilder("https://podcasts.example.com")

        val url = urlBuilder.buildEpisodeUrl("dQw4w9WgXcQ")

        url shouldBe "https://podcasts.example.com/episode/dQw4w9WgXcQ.mp3"
    }

    @Test
    fun `builds episode URL from request when no config`() {
        val urlBuilder = UrlBuilder("")

        val url = urlBuilder.buildEpisodeUrl("dQw4w9WgXcQ", "https", "localhost", 8080)

        url shouldBe "https://localhost:8080/episode/dQw4w9WgXcQ.mp3"
    }

    @Test
    fun `omits port 443 for https`() {
        val urlBuilder = UrlBuilder("")

        val url = urlBuilder.buildEpisodeUrl("abc123", "https", "example.com", 443)

        url shouldBe "https://example.com/episode/abc123.mp3"
    }

    @Test
    fun `omits port 80 for http`() {
        val urlBuilder = UrlBuilder("")

        val url = urlBuilder.buildEpisodeUrl("abc123", "http", "example.com", 80)

        url shouldBe "http://example.com/episode/abc123.mp3"
    }

    @Test
    fun `config base URL takes precedence over request`() {
        val urlBuilder = UrlBuilder("https://configured.com")

        val url = urlBuilder.buildEpisodeUrl("abc123", "http", "request.com", 9000)

        url shouldBe "https://configured.com/episode/abc123.mp3"
    }

    @Test
    fun `strips trailing slash from configured base URL`() {
        val urlBuilder = UrlBuilder("https://example.com/")

        val url = urlBuilder.buildEpisodeUrl("abc123")

        url shouldBe "https://example.com/episode/abc123.mp3"
    }
}
