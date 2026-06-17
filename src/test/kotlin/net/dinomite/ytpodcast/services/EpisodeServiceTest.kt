package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import net.dinomite.ytpodcast.util.ConversionProcess
import net.dinomite.ytpodcast.util.FfmpegExecutor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EpisodeServiceTest {
    @TempDir
    lateinit var tempDir: File

    private val audioService = mockk<AudioService>()
    private val cacheService = mockk<CacheService>(relaxed = true)
    private val ffmpegExecutor = mockk<FfmpegExecutor>()

    private val episodeService by lazy {
        EpisodeService(audioService, cacheService, ffmpegExecutor)
    }

    @Test
    fun `should download, convert, and cache audio`() {
        val videoId = "test-video"
        val rawFile = File(tempDir, "raw.tmp").apply { writeText("raw audio") }
        val tempCacheFile = File(tempDir, "cache.tmp")
        val outputStream = ByteArrayOutputStream()
        val mp3Data = "mp3 data".toByteArray()

        every { audioService.downloadToTempFile(videoId) } returns rawFile
        every { cacheService.createTempCacheFile(videoId) } returns tempCacheFile
        
        val conversion = mockk<ConversionProcess>()
        every { conversion.inputStream } returns ByteArrayInputStream(mp3Data)
        every { conversion.waitFor() } returns Unit
        every { ffmpegExecutor.startConversion(rawFile.absolutePath) } returns conversion

        val downloadedRaw = episodeService.downloadRawAudio(videoId)
        episodeService.streamAndCache(videoId, downloadedRaw, outputStream)

        outputStream.toByteArray() shouldBe mp3Data
        tempCacheFile.exists() shouldBe true
        tempCacheFile.readBytes() shouldBe mp3Data
        
        verify { cacheService.commitTempFile(videoId, tempCacheFile) }
        rawFile.exists() shouldBe false
    }
}
