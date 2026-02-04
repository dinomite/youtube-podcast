package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.junit.jupiter.api.Test

class AudioServiceTest {
    private val ytDlpExecutor = mockk<YtDlpExecutor>()
    private val audioService = AudioService(ytDlpExecutor)

    @Test
    fun `downloadToTempFile calls YtDlpExecutor with temp file`() {
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadAudio("abc123", capture(fileSlot)) } answers {
            fileSlot.captured.writeText("fake mp3 content")
        }

        val result = audioService.downloadToTempFile("abc123")

        try {
            verify { ytDlpExecutor.downloadAudio("abc123", any()) }
            result.exists() shouldBe true
            result.name shouldBe "abc123.mp3"
        } finally {
            result.delete()
        }
    }

    @Test
    fun `downloadToTempFile creates file in temp directory`() {
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadAudio("xyz789", capture(fileSlot)) } answers {
            fileSlot.captured.writeText("content")
        }

        val result = audioService.downloadToTempFile("xyz789")

        try {
            result.absolutePath shouldBe fileSlot.captured.absolutePath
        } finally {
            result.delete()
        }
    }
}
