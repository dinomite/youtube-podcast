# Cache Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add managed caching with LRU eviction, configurable size/count limits, to prevent disk exhaustion while optimizing performance.

**Architecture:** CacheService wraps AudioService, intercepts getAudioFile requests to check cache first, runs LRU eviction before downloads and on startup, tracks access via file modification times.

**Tech Stack:** Kotlin, Ktor 3.4+, Ktor application configuration with HOCON, SLF4J logging

---

## Task 1: Add size parsing utility function

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/util/SizeParser.kt`
- Test: `src/test/kotlin/net/dinomite/ytpodcast/util/SizeParserTest.kt`

**Step 1: Write the failing tests**

Create `src/test/kotlin/net/dinomite/ytpodcast/util/SizeParserTest.kt`:

```kotlin
package net.dinomite.ytpodcast.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SizeParserTest {
    @Test
    fun `parseSize handles bytes`() {
        parseSize("1024") shouldBe 1024L
        parseSize("100B") shouldBe 100L
        parseSize("50 B") shouldBe 50L
    }

    @Test
    fun `parseSize handles kilobytes`() {
        parseSize("1KB") shouldBe 1024L
        parseSize("5 KB") shouldBe 5120L
        parseSize("10kb") shouldBe 10240L
    }

    @Test
    fun `parseSize handles megabytes`() {
        parseSize("1MB") shouldBe 1048576L
        parseSize("5 MB") shouldBe 5242880L
        parseSize("100mb") shouldBe 104857600L
    }

    @Test
    fun `parseSize handles gigabytes`() {
        parseSize("1GB") shouldBe 1073741824L
        parseSize("5 GB") shouldBe 5368709120L
        parseSize("10gb") shouldBe 10737418240L
    }

    @Test
    fun `parseSize handles terabytes`() {
        parseSize("1TB") shouldBe 1099511627776L
        parseSize("2 tb") shouldBe 2199023255552L
    }

    @Test
    fun `parseSize treats zero as unlimited`() {
        parseSize("0") shouldBe 0L
        parseSize("0B") shouldBe 0L
        parseSize("0 GB") shouldBe 0L
    }

    @Test
    fun `parseSize is case insensitive`() {
        parseSize("5GB") shouldBe 5368709120L
        parseSize("5Gb") shouldBe 5368709120L
        parseSize("5gB") shouldBe 5368709120L
        parseSize("5gb") shouldBe 5368709120L
    }

    @Test
    fun `parseSize throws on invalid format`() {
        shouldThrow<IllegalArgumentException> {
            parseSize("invalid")
        }
        shouldThrow<IllegalArgumentException> {
            parseSize("5XB")
        }
        shouldThrow<IllegalArgumentException> {
            parseSize("GB5")
        }
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.SizeParserTest"`

Expected: Compilation error - `parseSize` function doesn't exist

**Step 3: Write minimal implementation**

Create `src/main/kotlin/net/dinomite/ytpodcast/util/SizeParser.kt`:

```kotlin
package net.dinomite.ytpodcast.util

/**
 * Parses a human-readable size string into bytes.
 *
 * Supports formats like:
 * - "1024" or "1024B" - bytes
 * - "5KB" or "5 KB" - kilobytes
 * - "100MB" or "100 MB" - megabytes
 * - "5GB" or "5 GB" - gigabytes
 * - "1TB" or "1 TB" - terabytes
 *
 * Case insensitive. "0" or "0B" means unlimited (returns 0).
 *
 * @param size The size string to parse
 * @return The size in bytes, or 0 for unlimited
 * @throws IllegalArgumentException if the format is invalid
 */
fun parseSize(size: String): Long {
    val pattern = """(\d+)\s*(B|KB|MB|GB|TB)?""".toRegex(RegexOption.IGNORE_CASE)
    val match = pattern.matchEntire(size.trim())
        ?: throw IllegalArgumentException("Invalid size format: $size")

    val amount = match.groupValues[1].toLong()
    val unit = match.groupValues[2].uppercase().ifEmpty { "B" }

    return when (unit) {
        "B" -> amount
        "KB" -> amount * 1024
        "MB" -> amount * 1024 * 1024
        "GB" -> amount * 1024 * 1024 * 1024
        "TB" -> amount * 1024 * 1024 * 1024 * 1024
        else -> throw IllegalArgumentException("Unknown unit: $unit")
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.SizeParserTest"`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/SizeParser.kt src/test/kotlin/net/dinomite/ytpodcast/util/SizeParserTest.kt
git commit -m "feat: add size parsing utility for human-readable sizes"
```

---

## Task 2: Create CacheConfig data class

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/config/CacheConfig.kt`
- Test: `src/test/kotlin/net/dinomite/ytpodcast/config/CacheConfigTest.kt`

**Step 1: Write the failing tests**

Create `src/test/kotlin/net/dinomite/ytpodcast/config/CacheConfigTest.kt`:

```kotlin
package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CacheConfigTest {
    @Test
    fun `CacheConfig holds maxSize`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxSize shouldBe 5368709120L
    }

    @Test
    fun `CacheConfig holds maxCount`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxCount shouldBe 100
    }

    @Test
    fun `CacheConfig holds directory`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.directory shouldBe "/tmp/cache"
    }

    @Test
    fun `CacheConfig allows zero for unlimited size`() {
        val config = CacheConfig(
            maxSize = 0L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxSize shouldBe 0L
    }

    @Test
    fun `CacheConfig allows zero for unlimited count`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 0,
            directory = "/tmp/cache"
        )

        config.maxCount shouldBe 0
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.config.CacheConfigTest"`

Expected: Compilation error - `CacheConfig` class doesn't exist

**Step 3: Write minimal implementation**

Create `src/main/kotlin/net/dinomite/ytpodcast/config/CacheConfig.kt`:

```kotlin
package net.dinomite.ytpodcast.config

/**
 * Configuration for the audio file cache.
 *
 * @property maxSize Maximum cache size in bytes (0 = unlimited)
 * @property maxCount Maximum number of cached files (0 = unlimited)
 * @property directory Directory path where cache files are stored
 */
data class CacheConfig(
    val maxSize: Long,
    val maxCount: Int,
    val directory: String,
)
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.config.CacheConfigTest"`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/config/CacheConfig.kt src/test/kotlin/net/dinomite/ytpodcast/config/CacheConfigTest.kt
git commit -m "feat: add CacheConfig data class for cache configuration"
```

---

## Task 3: Add tempDir to AppConfig

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/config/AppConfig.kt`
- Test: `src/test/kotlin/net/dinomite/ytpodcast/config/AppConfigTest.kt` (create)

**Step 1: Write the failing tests**

Create `src/test/kotlin/net/dinomite/ytpodcast/config/AppConfigTest.kt`:

```kotlin
package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppConfigTest {
    @Test
    fun `AppConfig holds baseUrl`() {
        val config = AppConfig(baseUrl = "http://example.com", tempDir = "/tmp")

        config.baseUrl shouldBe "http://example.com"
    }

    @Test
    fun `AppConfig holds tempDir`() {
        val config = AppConfig(baseUrl = "", tempDir = "/custom/temp")

        config.tempDir shouldBe "/custom/temp"
    }

    @Test
    fun `AppConfig allows empty tempDir`() {
        val config = AppConfig(baseUrl = "", tempDir = "")

        config.tempDir shouldBe ""
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.config.AppConfigTest"`

Expected: Compilation error - `tempDir` property doesn't exist

**Step 3: Update AppConfig data class**

Modify `src/main/kotlin/net/dinomite/ytpodcast/config/AppConfig.kt`:

```kotlin
package net.dinomite.ytpodcast.config

data class AppConfig(
    val baseUrl: String = "",
    val tempDir: String = "",
)
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.config.AppConfigTest"`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/config/AppConfig.kt src/test/kotlin/net/dinomite/ytpodcast/config/AppConfigTest.kt
git commit -m "feat: add tempDir field to AppConfig"
```

---

## Task 4: Load cache config from application.conf

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/Application.kt`
- Modify: `src/main/resources/application.conf`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/config/AppConfigTest.kt`

**Step 1: Write the failing tests**

Add to `src/test/kotlin/net/dinomite/ytpodcast/config/AppConfigTest.kt`:

```kotlin
import io.ktor.server.config.MapApplicationConfig
import net.dinomite.ytpodcast.loadAppConfig
import net.dinomite.ytpodcast.loadCacheConfig

@Test
fun `loadAppConfig loads tempDir from config`() {
    val config = MapApplicationConfig().apply {
        put("ytpodcast.tempDir", "/app/audio-cache")
    }

    val appConfig = loadAppConfig(config)

    appConfig.tempDir shouldBe "/app/audio-cache"
}

@Test
fun `loadAppConfig defaults tempDir to system temp when not in config`() {
    val config = MapApplicationConfig()

    val appConfig = loadAppConfig(config)

    appConfig.tempDir shouldBe System.getProperty("java.io.tmpdir")
}

@Test
fun `loadCacheConfig loads cache configuration`() {
    val config = MapApplicationConfig().apply {
        put("ytpodcast.cache.maxSize", "10GB")
        put("ytpodcast.cache.maxCount", "200")
    }

    val cacheConfig = loadCacheConfig(config, "/tmp")

    cacheConfig.maxSize shouldBe 10737418240L
    cacheConfig.maxCount shouldBe 200
    cacheConfig.directory shouldBe "/tmp"
}

@Test
fun `loadCacheConfig uses defaults when not in config`() {
    val config = MapApplicationConfig()

    val cacheConfig = loadCacheConfig(config, "/tmp")

    cacheConfig.maxSize shouldBe 5368709120L  // 5GB default
    cacheConfig.maxCount shouldBe 100  // 100 files default
    cacheConfig.directory shouldBe "/tmp"
}

@Test
fun `loadCacheConfig handles unlimited size`() {
    val config = MapApplicationConfig().apply {
        put("ytpodcast.cache.maxSize", "0")
        put("ytpodcast.cache.maxCount", "50")
    }

    val cacheConfig = loadCacheConfig(config, "/tmp")

    cacheConfig.maxSize shouldBe 0L
    cacheConfig.maxCount shouldBe 50
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.config.AppConfigTest"`

Expected: FAIL - `loadCacheConfig` function doesn't exist, tempDir not loaded

**Step 3: Update Application.kt to load config**

Modify `src/main/kotlin/net/dinomite/ytpodcast/Application.kt`:

```kotlin
package net.dinomite.ytpodcast

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.util.parseSize

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val appConfig = loadAppConfig(environment.config)
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(appConfig)
}

fun loadAppConfig(config: ApplicationConfig): AppConfig = AppConfig(
    baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: "",
    tempDir = config.propertyOrNull("ytpodcast.tempDir")?.getString() ?: System.getProperty("java.io.tmpdir"),
)

fun loadCacheConfig(config: ApplicationConfig, tempDir: String): CacheConfig {
    val sizeStr = config.propertyOrNull("ytpodcast.cache.maxSize")?.getString() ?: "5GB"
    val maxSize = parseSize(sizeStr)

    val maxCount = config.propertyOrNull("ytpodcast.cache.maxCount")?.getString()?.toInt() ?: 100

    return CacheConfig(maxSize, maxCount, tempDir)
}
```

**Step 4: Update application.conf**

Modify `src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ net.dinomite.ytpodcast.ApplicationKt.module ]
    }
}

ytpodcast {
    baseUrl = ""
    baseUrl = ${?BASE_URL}

    tempDir = ""
    tempDir = ${?TEMP_DIR}

    cache {
        maxSize = "5GB"
        maxSize = ${?CACHE_MAX_SIZE}

        maxCount = 100
        maxCount = ${?CACHE_MAX_COUNT}
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.config.AppConfigTest"`

Expected: All tests PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/Application.kt src/main/resources/application.conf src/test/kotlin/net/dinomite/ytpodcast/config/AppConfigTest.kt
git commit -m "feat: load tempDir and cache config from application.conf"
```

---

## Task 5: Update AudioService to accept tempDir parameter

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/services/AudioService.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/services/AudioServiceTest.kt`

**Step 1: Write the failing tests**

Modify `src/test/kotlin/net/dinomite/ytpodcast/services/AudioServiceTest.kt` to add tempDir parameter:

```kotlin
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
        val customTempDir = System.getProperty("java.io.tmpdir")
        val audioService = AudioService(ytDlpExecutor, customTempDir)
        val fileSlot = slot<File>()
        every { ytDlpExecutor.downloadAudio("abc123", capture(fileSlot)) } answers {
            fileSlot.captured.writeText("fake mp3 content")
        }

        audioService.downloadToTempFile("abc123")

        fileSlot.captured.parent shouldBe customTempDir
    }

    @Test
    fun `downloadToTempFile calls YtDlpExecutor with temp file`() {
        val audioService = AudioService(ytDlpExecutor, System.getProperty("java.io.tmpdir"))
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
        val audioService = AudioService(ytDlpExecutor, System.getProperty("java.io.tmpdir"))
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
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.AudioServiceTest"`

Expected: Compilation error - AudioService constructor doesn't accept tempDir

**Step 3: Update AudioService to accept tempDir**

Modify `src/main/kotlin/net/dinomite/ytpodcast/services/AudioService.kt`:

```kotlin
package net.dinomite.ytpodcast.services

import java.io.File
import net.dinomite.ytpodcast.util.YtDlpExecutor

/**
 * Service for downloading YouTube video audio as MP3 files.
 *
 * This service handles the conversion of YouTube videos to audio files
 * by delegating to [YtDlpExecutor] for the actual download and conversion.
 *
 * @property ytDlpExecutor The executor for running yt-dlp commands
 * @property tempDir The directory to use for temporary audio files
 */
class AudioService(
    private val ytDlpExecutor: YtDlpExecutor,
    private val tempDir: String,
) {
    /**
     * Downloads the audio from a YouTube video to a temporary file.
     *
     * Creates a temporary MP3 file in the configured temp directory and
     * downloads the audio from the specified video.
     *
     * @param videoId The YouTube video ID to download
     * @return The file containing the downloaded MP3 audio
     * @throws net.dinomite.ytpodcast.util.YtDlpException if the download fails
     */
    fun downloadToTempFile(videoId: String): File {
        val tempFile = File(tempDir, "$videoId.mp3")
        ytDlpExecutor.downloadAudio(videoId, tempFile)
        return tempFile
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.AudioServiceTest"`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/AudioService.kt src/test/kotlin/net/dinomite/ytpodcast/services/AudioServiceTest.kt
git commit -m "feat: make AudioService accept configurable tempDir parameter"
```

---

## Task 6: Create CacheService with basic pass-through

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt`
- Test: `src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt`

**Step 1: Write the failing tests**

Create `src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt`:

```kotlin
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
            maxSize = 0L,  // Unlimited for basic tests
            maxCount = 0,  // Unlimited for basic tests
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
        val expectedFile = File(tempDir, "$videoId.mp3")
        expectedFile.writeText("audio content")

        every { mockAudioService.downloadToTempFile(videoId) } returns expectedFile

        val result = cacheService.getAudioFile(videoId)

        result shouldBe expectedFile
        verify(exactly = 1) { mockAudioService.downloadToTempFile(videoId) }
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
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`

Expected: Compilation error - `CacheService` doesn't exist

**Step 3: Write minimal CacheService implementation**

Create `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt`:

```kotlin
package net.dinomite.ytpodcast.services

import java.io.File
import net.dinomite.ytpodcast.config.CacheConfig
import org.slf4j.LoggerFactory

/**
 * Service for managing cached audio files with LRU eviction.
 *
 * Provides caching layer over AudioService to avoid re-downloading
 * frequently accessed audio files. Enforces size and count limits
 * through LRU (Least Recently Used) eviction.
 *
 * @property audioService The underlying audio download service
 * @property config Cache configuration (limits and directory)
 */
class CacheService(
    private val audioService: AudioService,
    private val config: CacheConfig,
) {
    private val logger = LoggerFactory.getLogger(CacheService::class.java)

    /**
     * Gets an audio file, either from cache or by downloading.
     *
     * If the file exists in cache, returns it immediately and updates
     * its access time. If not in cache, downloads via AudioService.
     *
     * @param videoId The YouTube video ID
     * @return The audio file (from cache or newly downloaded)
     */
    fun getAudioFile(videoId: String): File {
        val cacheFile = File(config.directory, "$videoId.mp3")

        if (cacheFile.exists()) {
            logger.info("Cache HIT: videoId=$videoId")
            touchFile(cacheFile)
            return cacheFile
        }

        logger.info("Cache MISS: videoId=$videoId")
        logger.info("Downloading: videoId=$videoId")
        val downloadedFile = audioService.downloadToTempFile(videoId)
        logger.info("Download complete: videoId=$videoId, size=${formatSize(downloadedFile.length())}")
        return downloadedFile
    }

    /**
     * Initializes the cache by scanning the directory and enforcing limits.
     *
     * Should be called once at application startup before serving requests.
     */
    fun initialize() {
        val cacheDir = File(config.directory)

        if (!cacheDir.exists()) {
            logger.warn("Cache directory doesn't exist, creating: ${config.directory}")
            val created = cacheDir.mkdirs()
            if (!created) {
                logger.error("Failed to create cache directory: ${config.directory}")
                throw IllegalStateException("Cannot initialize cache")
            }
        }

        if (!cacheDir.canRead() || !cacheDir.canWrite()) {
            logger.error("Cache directory not accessible: ${config.directory}")
            throw IllegalStateException("Cannot access cache directory")
        }

        logger.info("Cache initialization starting: directory=${config.directory}")
        logger.info("Cache limits: maxSize=${formatSize(config.maxSize)}, maxCount=${config.maxCount}")

        val files = listCacheFiles()
        val totalSize = files.sumOf { it.length() }
        val totalCount = files.size

        logger.info("Cache current state: files=$totalCount, size=${formatSize(totalSize)}")
    }

    private fun touchFile(file: File) {
        val now = System.currentTimeMillis()
        val updated = file.setLastModified(now)

        if (!updated) {
            logger.warn("Failed to update access time: ${file.name}")
        }
    }

    private fun listCacheFiles(): List<File> {
        val cacheDir = File(config.directory)
        return cacheDir.listFiles { file -> file.isFile && file.extension == "mp3" }?.toList() ?: emptyList()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes == 0L -> "unlimited"
            bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt
git commit -m "feat: add CacheService with basic pass-through functionality"
```

---

## Task 7: Add LRU eviction logic to CacheService

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt`

**Step 1: Write the failing tests**

Add to `src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt`:

```kotlin
@Test
fun `eviction removes oldest files when count limit exceeded`() {
    val config = CacheConfig(
        maxSize = 0L,  // Unlimited size
        maxCount = 2,  // Max 2 files
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

    // Mock download to trigger eviction
    every { mockAudioService.downloadToTempFile("video4") } answers {
        File(tempDir, "video4.mp3").apply { writeText("content4") }
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
        maxSize = 20L,  // Max 20 bytes
        maxCount = 0,   // Unlimited count
        directory = tempDir.absolutePath
    )
    cacheService = CacheService(mockAudioService, config)
    cacheService.initialize()

    // Create files totaling > 20 bytes
    val file1 = File(tempDir, "video1.mp3").apply {
        writeText("12345678")  // 8 bytes
        setLastModified(System.currentTimeMillis() - 3000)
    }

    val file2 = File(tempDir, "video2.mp3").apply {
        writeText("12345678")  // 8 bytes
        setLastModified(System.currentTimeMillis() - 2000)
    }

    // Mock download (will add 8 more bytes, exceeding limit)
    every { mockAudioService.downloadToTempFile("video3") } answers {
        File(tempDir, "video3.mp3").apply { writeText("12345678") }
    }

    cacheService.getAudioFile("video3")

    // Oldest file should be deleted to make room
    file1.exists() shouldBe false
    file2.exists() shouldBe true
}

@Test
fun `eviction respects both size and count limits`() {
    val config = CacheConfig(
        maxSize = 100L,  // 100 bytes
        maxCount = 3,    // 3 files
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
        maxSize = 0L,    // Unlimited
        maxCount = 2,    // Max 2 files
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
fun `unlimited count disables count limit`() {
    val config = CacheConfig(
        maxSize = 100L,  // 100 bytes
        maxCount = 0,    // Unlimited
        directory = tempDir.absolutePath
    )
    cacheService = CacheService(mockAudioService, config)
    cacheService.initialize()

    // Create many small files (would exceed count limit if enabled)
    repeat(10) { i ->
        File(tempDir, "video$i.mp3").apply {
            writeText("x")  // 1 byte each, well under size limit
            setLastModified(System.currentTimeMillis() - (10 - i) * 1000L)
        }
    }

    // All should still exist (count limit disabled)
    tempDir.listFiles().size shouldBe 10
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`

Expected: FAIL - eviction not implemented, files not deleted

**Step 3: Add eviction logic to CacheService**

Modify `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt`, add eviction method:

```kotlin
fun getAudioFile(videoId: String): File {
    val cacheFile = File(config.directory, "$videoId.mp3")

    if (cacheFile.exists()) {
        logger.info("Cache HIT: videoId=$videoId")
        touchFile(cacheFile)
        return cacheFile
    }

    logger.info("Cache MISS: videoId=$videoId")
    evictIfNeeded()  // Add this line
    logger.info("Downloading: videoId=$videoId")
    val downloadedFile = audioService.downloadToTempFile(videoId)
    logger.info("Download complete: videoId=$videoId, size=${formatSize(downloadedFile.length())}")
    return downloadedFile
}

fun initialize() {
    val cacheDir = File(config.directory)

    if (!cacheDir.exists()) {
        logger.warn("Cache directory doesn't exist, creating: ${config.directory}")
        val created = cacheDir.mkdirs()
        if (!created) {
            logger.error("Failed to create cache directory: ${config.directory}")
            throw IllegalStateException("Cannot initialize cache")
        }
    }

    if (!cacheDir.canRead() || !cacheDir.canWrite()) {
        logger.error("Cache directory not accessible: ${config.directory}")
        throw IllegalStateException("Cannot access cache directory")
    }

    logger.info("Cache initialization starting: directory=${config.directory}")
    logger.info("Cache limits: maxSize=${formatSize(config.maxSize)}, maxCount=${config.maxCount}")

    val files = listCacheFiles()
    val totalSize = files.sumOf { it.length() }
    val totalCount = files.size

    logger.info("Cache current state: files=$totalCount, size=${formatSize(totalSize)}")

    evictIfNeeded()  // Add this line

    val postFiles = listCacheFiles()
    val postSize = postFiles.sumOf { it.length() }
    val postCount = postFiles.size
    logger.info("Cache post-cleanup: files=$postCount, size=${formatSize(postSize)}")
}

private fun evictIfNeeded() {
    val files = listCacheFiles()
    val totalSize = files.sumOf { it.length() }
    val totalCount = files.size

    if (isWithinLimits(totalSize, totalCount)) {
        return
    }

    logger.info(
        "Eviction starting: currentFiles=$totalCount, currentSize=${formatSize(totalSize)}, reason=over-limit"
    )

    // Sort by last modified time (oldest first = least recently used)
    val sortedFiles = files.sortedBy { it.lastModified() }

    var currentSize = totalSize
    var currentCount = totalCount
    var removedCount = 0
    var freedSpace = 0L

    for (file in sortedFiles) {
        if (isWithinLimits(currentSize, currentCount)) {
            break
        }

        val fileSize = file.length()
        val ageMinutes = (System.currentTimeMillis() - file.lastModified()) / 60000

        val deleted = file.delete()
        if (deleted) {
            currentSize -= fileSize
            currentCount -= 1
            removedCount += 1
            freedSpace += fileSize
            logger.info("Evicted: file=${file.name}, size=${formatSize(fileSize)}, age=${ageMinutes}min")
        } else {
            logger.warn("Failed to delete file: ${file.name}")
        }
    }

    logger.info("Eviction complete: removedFiles=$removedCount, freedSpace=${formatSize(freedSpace)}")
}

private fun isWithinLimits(currentSize: Long, currentCount: Int): Boolean {
    val sizeOk = config.maxSize == 0L || currentSize <= config.maxSize
    val countOk = config.maxCount == 0 || currentCount <= config.maxCount
    return sizeOk && countOk
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`

Expected: All tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt
git commit -m "feat: add LRU eviction logic to CacheService"
```

---

## Task 8: Wire CacheService into Application

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/Application.kt`
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`

**Step 1: Update Application.kt to create CacheService**

Modify `src/main/kotlin/net/dinomite/ytpodcast/Application.kt`:

```kotlin
package net.dinomite.ytpodcast

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.plugins.configureHTTP
import net.dinomite.ytpodcast.plugins.configureMonitoring
import net.dinomite.ytpodcast.plugins.configureRouting
import net.dinomite.ytpodcast.plugins.configureSerialization
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.CacheService
import net.dinomite.ytpodcast.util.YtDlpExecutor
import net.dinomite.ytpodcast.util.parseSize

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val appConfig = loadAppConfig(environment.config)
    val cacheConfig = loadCacheConfig(environment.config, appConfig.tempDir)

    val ytDlpExecutor = YtDlpExecutor()
    val audioService = AudioService(ytDlpExecutor, cacheConfig.directory)
    val cacheService = CacheService(audioService, cacheConfig)

    cacheService.initialize()

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(appConfig, cacheService)
}

fun loadAppConfig(config: ApplicationConfig): AppConfig = AppConfig(
    baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: "",
    tempDir = config.propertyOrNull("ytpodcast.tempDir")?.getString() ?: System.getProperty("java.io.tmpdir"),
)

fun loadCacheConfig(config: ApplicationConfig, tempDir: String): CacheConfig {
    val sizeStr = config.propertyOrNull("ytpodcast.cache.maxSize")?.getString() ?: "5GB"
    val maxSize = parseSize(sizeStr)

    val maxCount = config.propertyOrNull("ytpodcast.cache.maxCount")?.getString()?.toInt() ?: 100

    return CacheConfig(maxSize, maxCount, tempDir)
}
```

**Step 2: Update Routing.kt to accept CacheService**

Modify `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`:

Replace AudioService with CacheService throughout:

```kotlin
package net.dinomite.ytpodcast.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.dinomite.ytpodcast.config.AppConfig
import net.dinomite.ytpodcast.models.ErrorResponse
import net.dinomite.ytpodcast.services.CacheService
import net.dinomite.ytpodcast.services.RssFeedService
import net.dinomite.ytpodcast.services.YouTubeMetadataService
import net.dinomite.ytpodcast.util.UrlBuilder
import net.dinomite.ytpodcast.util.YtDlpException
import net.dinomite.ytpodcast.util.YtDlpExecutor
import org.slf4j.LoggerFactory

fun Application.configureRouting(
    appConfig: AppConfig,
    cacheService: CacheService,
) {
    val ytDlpExecutor = YtDlpExecutor()
    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val urlBuilder = UrlBuilder(appConfig.baseUrl)
    val rssFeedService = RssFeedService(urlBuilder)

    val handlers = RouteHandlers(youTubeMetadataService, rssFeedService, cacheService)

    routing {
        get("/") {
            call.respondText("YouTube to Podcast RSS Feed Converter")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        handlers.registerShowRoute(this)
        handlers.registerEpisodeRoute(this)
    }
}

// Keep the test overload that accepts YtDlpExecutor
fun Application.configureRouting(
    appConfig: AppConfig,
    ytDlpExecutor: YtDlpExecutor,
) {
    val cacheConfig = loadCacheConfig(environment.config, appConfig.tempDir)
    val audioService = AudioService(ytDlpExecutor, cacheConfig.directory)
    val cacheService = CacheService(audioService, cacheConfig)

    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val urlBuilder = UrlBuilder(appConfig.baseUrl)
    val rssFeedService = RssFeedService(urlBuilder)

    val handlers = RouteHandlers(youTubeMetadataService, rssFeedService, cacheService)

    routing {
        get("/") {
            call.respondText("YouTube to Podcast RSS Feed Converter")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        handlers.registerShowRoute(this)
        handlers.registerEpisodeRoute(this)
    }
}

private class RouteHandlers(
    private val youTubeMetadataService: YouTubeMetadataService,
    private val rssFeedService: RssFeedService,
    private val cacheService: CacheService,
) {
    private val logger = LoggerFactory.getLogger(RouteHandlers::class.java)

    fun registerShowRoute(routing: Routing) {
        routing.get("/show/{playlistId}") {
            // ... existing implementation
        }
    }

    fun registerEpisodeRoute(routing: Routing) {
        routing.get("/episode/{videoId}.mp3") {
            val videoId = call.parameters["videoId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))

            try {
                val audioFile = cacheService.getAudioFile(videoId)  // Changed from audioService
                call.response.header(HttpHeaders.ContentType, ContentType.Audio.MPEG.toString())
                call.respondFile(audioFile)
            } catch (e: YtDlpException) {
                val status = YtDlpErrorConfig.determineStatusCode(e.message ?: "")
                call.respond(status, ErrorResponse(e.message ?: "Failed to download audio"))
            }
        }
    }

    private suspend fun ApplicationCall.buildUrlBuilder(): UrlBuilder {
        return UrlBuilder("http://${request.host()}:${request.port()}")
    }
}

// ... rest of file unchanged
```

**Step 3: Run all tests to check for compilation errors**

Run: `./gradlew test`

Expected: Compilation errors in tests that need updating

**Step 4: Fix compilation - add missing imports to Routing.kt**

Add these imports to `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`:

```kotlin
import net.dinomite.ytpodcast.loadCacheConfig
import net.dinomite.ytpodcast.services.AudioService
```

**Step 5: Run all tests again**

Run: `./gradlew test`

Expected: Most tests pass, integration tests may need updates

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/Application.kt src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt
git commit -m "feat: wire CacheService into application routing"
```

---

## Task 9: Fix integration tests

**Files:**
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt`

**Step 1: Check which tests are failing**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest"`

Expected: Tests may fail due to routing changes

**Step 2: Read IntegrationTest to understand structure**

Read `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt` to see how tests are structured

**Step 3: Update IntegrationTest if needed**

The test overload `configureRouting(appConfig, ytDlpExecutor)` should handle the wiring automatically. Tests should work without changes, but verify.

**Step 4: Run integration tests**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest"`

Expected: All tests PASS

**Step 5: Run all tests**

Run: `./gradlew test`

Expected: All tests PASS

**Step 6: Commit if changes were needed**

```bash
# Only if IntegrationTest.kt was modified
git add src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt
git commit -m "test: update integration tests for CacheService"
```

---

## Task 10: Update documentation

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

**Step 1: Update README.md configuration section**

Modify the "Configuration" section in `README.md`:

Add documentation for cache configuration:

```markdown
## Configuration

The application can be configured via environment variables:

- `PORT` - Server port (default: 8080)
- `BASE_URL` - Base URL for generating episode URLs in RSS feeds (optional)
- `TEMP_DIR` - Directory for cached audio files (default: system temp directory)
- `CACHE_MAX_SIZE` - Maximum cache size (default: 5GB, "0" for unlimited)
- `CACHE_MAX_COUNT` - Maximum number of cached files (default: 100, 0 for unlimited)
- `JAVA_OPTS` - JVM options (Docker only, default: `-Xmx512m -Xms256m`)

### Cache Management

Downloaded audio files are cached to improve performance. The cache uses LRU (Least Recently Used) eviction when limits are reached:

- Size and count limits are enforced simultaneously
- Least recently accessed files are deleted first
- Cache is cleaned up on startup and before each download
- Set limits to "0" to disable that constraint
```

**Step 2: Update CLAUDE.md architecture section**

Modify `CLAUDE.md` to document CacheService:

```markdown
**Source structure**:
- `plugins/` - Ktor plugin configurations (Serialization, Monitoring, HTTP, Routing)
- `models/` - Data classes (ErrorResponse, PlaylistMetadata, VideoMetadata)
- `services/` - Business logic services (RssFeedService, AudioService, CacheService, YouTubeMetadataService)
- `util/` - Utilities (YtDlpExecutor, UrlBuilder, SizeParser, YtDlpException)
- `config/` - Application configuration (AppConfig: baseUrl, tempDir; CacheConfig: maxSize, maxCount, directory)
```

Add CacheService description:

```markdown
**Service Layer**:
- `YouTubeMetadataService` - Fetches playlist/video metadata via yt-dlp
- `RssFeedService` - Generates RSS XML with iTunes podcast namespace, handles XML escaping, sorting
- `AudioService` - Downloads audio to configured temp directory
- `CacheService` - Manages cached audio files with LRU eviction, enforces size/count limits
- `UrlBuilder` - Intelligent URL generation using AppConfig or request context
```

**Step 3: Run tests to verify nothing broke**

Run: `./gradlew test`

Expected: All tests PASS

**Step 4: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: document cache management configuration and architecture"
```

---

## Task 11: Manual verification

**Files:**
- N/A (manual testing)

**Step 1: Run the application**

```bash
./gradlew run
```

Check logs for:
- "Cache initialization starting"
- "Cache limits: maxSize=5.00 GB, maxCount=100"
- "Cache current state: files=X, size=Y"

**Step 2: Test health endpoint**

In another terminal:
```bash
curl http://localhost:8080/health
```

Expected: `{"status":"healthy"}`

**Step 3: Test with custom cache config**

Stop the server (Ctrl+C), then:

```bash
CACHE_MAX_SIZE=1GB CACHE_MAX_COUNT=50 ./gradlew run
```

Check logs show:
- "Cache limits: maxSize=1.00 GB, maxCount=50"

**Step 4: Clean up**

Stop the server with Ctrl+C

---

## Summary

This plan implements managed cache with LRU eviction by:

1. **Configuration foundation** - Size parsing utility, CacheConfig data class, loading from application.conf
2. **Basic CacheService** - Pass-through to AudioService, cache hit/miss detection, file touching
3. **Eviction logic** - LRU eviction based on file modification times, respects both size and count limits
4. **Integration** - Wire through Application.module(), update Routing to use CacheService
5. **Documentation** - Update README and CLAUDE.md with cache configuration

The implementation follows:
- **TDD**: Tests written first for each component
- **DRY**: Reuse utilities, avoid duplication
- **YAGNI**: Only cache management needed, no extras
- **Frequent commits**: One commit per completed task