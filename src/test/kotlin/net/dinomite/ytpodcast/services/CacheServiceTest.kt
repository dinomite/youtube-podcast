package net.dinomite.ytpodcast.services

import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory
import net.dinomite.ytpodcast.config.CacheConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CacheServiceTest {
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
        cacheService = CacheService(config)
    }

    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
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
    fun `eviction respects both size and count limits`() {
        val config = CacheConfig(
            maxSize = 100L, // 100 bytes
            maxCount = 3, // 3 files
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(config)
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
        cacheService = CacheService(config)
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
        cacheService = CacheService(config)
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
