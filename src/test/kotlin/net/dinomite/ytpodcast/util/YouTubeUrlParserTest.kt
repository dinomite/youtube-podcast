package net.dinomite.ytpodcast.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class YouTubeUrlParserTest {
    @Test
    fun `extracts playlist ID from canonical playlist URL`() {
        extractPlaylistId("https://www.youtube.com/playlist?list=PLxxxxxx") shouldBe "PLxxxxxx"
    }

    @Test
    fun `extracts playlist ID from watch URL with list param`() {
        extractPlaylistId("https://www.youtube.com/watch?v=abc123&list=PLxxxxxx") shouldBe "PLxxxxxx"
    }

    @Test
    fun `extracts playlist ID from short youtu be URL`() {
        extractPlaylistId("https://youtu.be/abc123?list=PLxxxxxx") shouldBe "PLxxxxxx"
    }

    @Test
    fun `returns null for URL with no list param`() {
        extractPlaylistId("https://www.youtube.com/watch?v=abc123") shouldBe null
    }

    @Test
    fun `returns null for malformed URL`() {
        extractPlaylistId("not a url at all") shouldBe null
    }

    @Test
    fun `returns null for empty string`() {
        extractPlaylistId("") shouldBe null
    }
}
