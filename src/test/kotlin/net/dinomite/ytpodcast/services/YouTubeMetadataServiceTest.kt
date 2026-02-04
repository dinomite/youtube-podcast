package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.junit.jupiter.api.Test

class YouTubeMetadataServiceTest {
    private val ytDlpExecutor = mockk<YtDlpExecutor>()
    private val service = YouTubeMetadataService(ytDlpExecutor)

    @Test
    fun `getPlaylist delegates to YtDlpExecutor`() {
        val expectedPlaylist = PlaylistMetadata(
            id = "PLtest",
            title = "Test Playlist",
            entries = listOf(VideoMetadata(id = "v1", title = "Video 1")),
        )
        every { ytDlpExecutor.fetchPlaylist("PLtest") } returns expectedPlaylist

        val result = service.getPlaylist("PLtest")

        result shouldBe expectedPlaylist
        verify { ytDlpExecutor.fetchPlaylist("PLtest") }
    }
}
