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

    @Test
    fun `downloadToTempFile uses configured temp directory`() {
        val customTempDir = System.getProperty("java.io.tmpdir").trimEnd('/')
        val audioService = AudioService(ytDlpExecutor, customTempDir)
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadRawAudio("abc123", capture(fileSlot)) } answers {
            fileSlot.captured.writeText("fake mp3 content")
        }

        audioService.downloadToTempFile("abc123")

        fileSlot.captured.parent shouldBe customTempDir
    }

    @Test
    fun `downloadToTempFile calls YtDlpExecutor with temp file`() {
        val audioService = AudioService(ytDlpExecutor, System.getProperty("java.io.tmpdir"))
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadRawAudio("abc123", capture(fileSlot)) } answers {
            fileSlot.captured.writeText("fake mp3 content")
        }

        val result = audioService.downloadToTempFile("abc123")

        try {
            verify { ytDlpExecutor.downloadRawAudio("abc123", any()) }
            result.exists() shouldBe true
            result.name shouldBe "abc123.raw"
        } finally {
            result.delete()
        }
    }

    @Test
    fun `downloadToTempFile creates file in temp directory`() {
        val audioService = AudioService(ytDlpExecutor, System.getProperty("java.io.tmpdir"))
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadRawAudio("xyz789", capture(fileSlot)) } answers {
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
