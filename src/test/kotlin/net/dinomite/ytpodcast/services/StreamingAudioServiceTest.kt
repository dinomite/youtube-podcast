package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import net.dinomite.ytpodcast.util.ConversionProcess
import net.dinomite.ytpodcast.util.FfmpegExecutor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StreamingAudioServiceTest {
    private val mockAudioService = mockk<AudioService>()
    private val mockFfmpegExecutor = mockk<FfmpegExecutor>()
    private lateinit var cacheDir: File
    private lateinit var service: StreamingAudioService

    @BeforeEach
    fun setup() {
        cacheDir = createTempDirectory("streaming-test-cache").toFile()
        service = StreamingAudioService(mockAudioService, mockFfmpegExecutor, cacheDir.absolutePath)
    }

    @AfterEach
    fun cleanup() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `streamConvertedAudio writes ffmpeg output to outputStream`() {
        val rawFile = File(cacheDir, "vid1.raw").apply { writeText("raw audio") }
        val mp3Bytes = "fake mp3 data".toByteArray()

        every { mockAudioService.downloadToTempFile("vid1") } returns rawFile

        val mockProcess = mockk<ConversionProcess>(relaxed = true)
        every { mockProcess.inputStream } returns ByteArrayInputStream(mp3Bytes)
        every { mockFfmpegExecutor.startConversion(rawFile.absolutePath) } returns mockProcess

        val output = ByteArrayOutputStream()
        service.streamConvertedAudio("vid1", output)

        output.toByteArray() shouldBe mp3Bytes
    }

    @Test
    fun `streamConvertedAudio caches the converted MP3`() {
        val rawFile = File(cacheDir, "vid2.raw").apply { writeText("raw audio") }
        val mp3Bytes = "cached mp3 data".toByteArray()

        every { mockAudioService.downloadToTempFile("vid2") } returns rawFile

        val mockProcess = mockk<ConversionProcess>(relaxed = true)
        every { mockProcess.inputStream } returns ByteArrayInputStream(mp3Bytes)
        every { mockFfmpegExecutor.startConversion(rawFile.absolutePath) } returns mockProcess

        val output = ByteArrayOutputStream()
        service.streamConvertedAudio("vid2", output)

        val cachedFile = File(cacheDir, "vid2.mp3")
        cachedFile.exists() shouldBe true
        cachedFile.readBytes() shouldBe mp3Bytes
    }

    @Test
    fun `streamConvertedAudio deletes raw file after conversion`() {
        val rawFile = File(cacheDir, "vid3.raw").apply { writeText("raw audio") }
        val mp3Bytes = "mp3 data".toByteArray()

        every { mockAudioService.downloadToTempFile("vid3") } returns rawFile

        val mockProcess = mockk<ConversionProcess>(relaxed = true)
        every { mockProcess.inputStream } returns ByteArrayInputStream(mp3Bytes)
        every { mockFfmpegExecutor.startConversion(rawFile.absolutePath) } returns mockProcess

        val output = ByteArrayOutputStream()
        service.streamConvertedAudio("vid3", output)

        rawFile.exists() shouldBe false
    }

    @Test
    fun `streamConvertedAudio waits for ffmpeg process to complete`() {
        val rawFile = File(cacheDir, "vid4.raw").apply { writeText("raw audio") }

        every { mockAudioService.downloadToTempFile("vid4") } returns rawFile

        val mockProcess = mockk<ConversionProcess>(relaxed = true)
        every { mockProcess.inputStream } returns ByteArrayInputStream("mp3".toByteArray())
        every { mockFfmpegExecutor.startConversion(rawFile.absolutePath) } returns mockProcess

        val output = ByteArrayOutputStream()
        service.streamConvertedAudio("vid4", output)

        verify { mockProcess.waitFor(any()) }
    }
}
