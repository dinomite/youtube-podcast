package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.io.path.createTempDirectory
import net.dinomite.ytpodcast.config.CacheConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CacheServiceTest {
    private val mockAudioService = mockk<AudioService>()
    private lateinit var tempDir: File
    private lateinit var cacheService: CacheService

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("cache-test").toFile()
        val config = CacheConfig(
            maxSize = 0L, // Unlimited for basic tests
            maxCount = 0, // Unlimited for basic tests
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(mockAudioService, config)
    }

    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `getAudioFile delegates to AudioService when file not in cache`() {
        val videoId = "test123"
        // AudioService downloads to a different temp directory, not the cache
        val audioServiceTempDir = createTempDirectory("audio-temp").toFile()
        val expectedFile = File(audioServiceTempDir, "$videoId.mp3")
        expectedFile.writeText("audio content")

        every { mockAudioService.downloadToTempFile(videoId) } returns expectedFile

        val result = cacheService.getAudioFile(videoId)

        result shouldBe expectedFile
        verify(exactly = 1) { mockAudioService.downloadToTempFile(videoId) }

        // Cleanup
        audioServiceTempDir.deleteRecursively()
    }

    @Test
    fun `getAudioFile returns existing file from cache without downloading`() {
        val videoId = "cached123"
        val cachedFile = File(tempDir, "$videoId.mp3")
        cachedFile.writeText("existing content")

        val result = cacheService.getAudioFile(videoId)

        result shouldBe cachedFile
        result.readText() shouldBe "existing content"
        verify(exactly = 0) { mockAudioService.downloadToTempFile(any()) }
    }

    @Test
    fun `initialize scans cache directory successfully`() {
        // Create a few files
        File(tempDir, "video1.mp3").writeText("content1")
        File(tempDir, "video2.mp3").writeText("content2")

        // Should not throw
        cacheService.initialize()
    }
}
