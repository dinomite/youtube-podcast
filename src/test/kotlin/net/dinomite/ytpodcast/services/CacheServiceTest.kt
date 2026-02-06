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
        val tempFile = File(audioServiceTempDir, "$videoId.mp3")
        tempFile.writeText("audio content")

        every { mockAudioService.downloadToTempFile(videoId) } returns tempFile

        val result = cacheService.getAudioFile(videoId)

        // File should be moved to cache directory
        val expectedCacheFile = File(tempDir, "$videoId.mp3")
        result shouldBe expectedCacheFile
        result.exists() shouldBe true
        result.readText() shouldBe "audio content"

        // Original temp file should no longer exist (was moved)
        tempFile.exists() shouldBe false

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

    @Test
    fun `eviction removes oldest files when count limit exceeded`() {
        val config = CacheConfig(
            maxSize = 0L, // Unlimited size
            maxCount = 2, // Max 2 files
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(mockAudioService, config)
        cacheService.initialize()

        // Create 3 files with different timestamps
        val file1 = File(tempDir, "video1.mp3").apply { writeText("content1") }
        Thread.sleep(10)
        file1.setLastModified(System.currentTimeMillis() - 3000)

        val file2 = File(tempDir, "video2.mp3").apply { writeText("content2") }
        Thread.sleep(10)
        file2.setLastModified(System.currentTimeMillis() - 2000)

        val file3 = File(tempDir, "video3.mp3").apply { writeText("content3") }
        Thread.sleep(10)
        file3.setLastModified(System.currentTimeMillis() - 1000)

        // Mock download to trigger eviction - downloads to temp, not cache
        val audioTempDir = createTempDirectory("audio-temp").toFile()
        every { mockAudioService.downloadToTempFile("video4") } answers {
            File(audioTempDir, "video4.mp3").apply { writeText("content4") }
        }

        cacheService.getAudioFile("video4")

        // Oldest file (video1) should be deleted
        file1.exists() shouldBe false
        file2.exists() shouldBe true
        file3.exists() shouldBe true
    }

    @Test
    fun `eviction removes oldest files when size limit exceeded`() {
        val config = CacheConfig(
            maxSize = 20L, // Max 20 bytes
            maxCount = 0, // Unlimited count
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(mockAudioService, config)
        cacheService.initialize()

        // Create files totaling > 20 bytes (already over limit)
        val file1 = File(tempDir, "video1.mp3").apply {
            writeText("12345678") // 8 bytes
            setLastModified(System.currentTimeMillis() - 3000)
        }

        val file2 = File(tempDir, "video2.mp3").apply {
            writeText("12345678") // 8 bytes
            setLastModified(System.currentTimeMillis() - 2000)
        }

        val file3 = File(tempDir, "video3.mp3").apply {
            writeText("12345678") // 8 bytes
            setLastModified(System.currentTimeMillis() - 1000)
        }

        // Mock download - downloads to temp, not cache
        val audioTempDir = createTempDirectory("audio-temp").toFile()
        every { mockAudioService.downloadToTempFile("video4") } answers {
            File(audioTempDir, "video4.mp3").apply { writeText("small") }
        }

        cacheService.getAudioFile("video4")

        // Oldest file should be deleted to get under limit
        file1.exists() shouldBe false
        file2.exists() shouldBe true
        file3.exists() shouldBe true
    }

    @Test
    fun `eviction respects both size and count limits`() {
        val config = CacheConfig(
            maxSize = 100L, // 100 bytes
            maxCount = 3, // 3 files
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(mockAudioService, config)
        cacheService.initialize()

        // Create 3 files
        File(tempDir, "video1.mp3").apply {
            writeText("small")
            setLastModified(System.currentTimeMillis() - 3000)
        }
        File(tempDir, "video2.mp3").apply {
            writeText("small")
            setLastModified(System.currentTimeMillis() - 2000)
        }
        File(tempDir, "video3.mp3").apply {
            writeText("small")
            setLastModified(System.currentTimeMillis() - 1000)
        }

        val files = tempDir.listFiles()
        files.size shouldBe 3

        // All within limits, no eviction
        cacheService.initialize()
        tempDir.listFiles().size shouldBe 3
    }

    @Test
    fun `unlimited size disables size limit`() {
        val config = CacheConfig(
            maxSize = 0L, // Unlimited
            maxCount = 2, // Max 2 files
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(mockAudioService, config)
        cacheService.initialize()

        // Create 2 large files (would exceed any reasonable size limit)
        File(tempDir, "video1.mp3").apply {
            writeText("x".repeat(1000000))
            setLastModified(System.currentTimeMillis() - 2000)
        }
        File(tempDir, "video2.mp3").apply {
            writeText("x".repeat(1000000))
            setLastModified(System.currentTimeMillis() - 1000)
        }

        // Both should still exist (size limit disabled)
        tempDir.listFiles().size shouldBe 2
    }

    @Test
    fun `getCachedFile returns file when it exists in cache`() {
        val videoId = "cached456"
        val cachedFile = File(tempDir, "$videoId.mp3")
        cachedFile.writeText("cached content")

        val result = cacheService.getCachedFile(videoId)

        result shouldBe cachedFile
        result!!.readText() shouldBe "cached content"
    }

    @Test
    fun `getCachedFile returns null when file not in cache`() {
        val result = cacheService.getCachedFile("nonexistent")

        result shouldBe null
    }

    @Test
    fun `unlimited count disables count limit`() {
        val config = CacheConfig(
            maxSize = 100L, // 100 bytes
            maxCount = 0, // Unlimited
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(mockAudioService, config)
        cacheService.initialize()

        // Create many small files (would exceed count limit if enabled)
        repeat(10) { i ->
            File(tempDir, "video$i.mp3").apply {
                writeText("x") // 1 byte each, well under size limit
                setLastModified(System.currentTimeMillis() - (10 - i) * 1000L)
            }
        }

        // All should still exist (count limit disabled)
        tempDir.listFiles().size shouldBe 10
    }
}
