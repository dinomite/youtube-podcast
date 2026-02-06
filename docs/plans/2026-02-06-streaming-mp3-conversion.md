# Streaming MP3 Conversion Pipeline

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Stream MP3 audio to HTTP clients by splitting download (yt-dlp) from conversion (ffmpeg), so bytes flow to the client as soon as conversion begins rather than waiting for yt-dlp's full download+conversion to complete.

**Architecture:** Two-phase pipeline: (1) yt-dlp downloads raw audio without conversion (`-f bestaudio`), (2) ffmpeg converts to MP3 streaming to stdout, which is tee'd to both the HTTP response and a cache file. Cached requests still serve from disk via `respondFile()`. An `FfmpegExecutor` utility handles the ffmpeg subprocess, paralleling the existing `YtDlpExecutor` pattern.

**Tech Stack:** Ktor 3.4+ (respondOutputStream, chunked transfer), ffmpeg via ProcessBuilder, JUnit 5, Kotest matchers, MockK

---

## Current Architecture (for context)

```
/episode/{videoId}.mp3 request
  → CacheService.getAudioFile(videoId)
    → cache hit? return File
    → cache miss: AudioService.downloadToTempFile(videoId)
      → YtDlpExecutor.downloadAudio(videoId, file)
        → yt-dlp -x --audio-format mp3 --audio-quality 0 -o {path} {url}
      → returns File
    → rename temp → cache
    → return File
  → call.respondFile(file)
```

**Problem:** Client waits for yt-dlp's full download+conversion before receiving any bytes.

## Target Architecture

```
/episode/{videoId}.mp3 request
  → CacheService checks cache
    → cache hit? call.respondFile(cachedFile)  [unchanged]
    → cache miss:
      → AudioService.downloadRawAudio(videoId) → raw file on disk
      → call.respondOutputStream:
        → FfmpegExecutor converts raw → MP3, streaming stdout
        → each chunk: write to response + write to cache file
        → on success: raw file deleted, cache file kept
        → on error: cache file deleted
```

---

## Task 1: Add `FfmpegExecutor` with tests

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/util/FfmpegExecutor.kt`
- Create: `src/test/kotlin/net/dinomite/ytpodcast/util/FfmpegExecutorTest.kt`

This utility wraps `ffmpeg` as a subprocess. It starts an ffmpeg process that reads a raw audio file and writes MP3 to stdout. It exposes the process's stdout as an `InputStream` for the caller to consume.

**Step 1: Write the test for `buildConvertCommand`**

In `FfmpegExecutorTest.kt`:

```kotlin
package net.dinomite.ytpodcast.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FfmpegExecutorTest {
    @Test
    fun `buildConvertCommand builds correct ffmpeg command`() {
        val command = FfmpegExecutor.buildConvertCommand("/tmp/input.webm")

        command shouldBe listOf(
            "ffmpeg",
            "-i", "/tmp/input.webm",
            "-codec:a", "libmp3lame",
            "-q:a", "0",
            "-f", "mp3",
            "pipe:1",
        )
    }
}
```

**Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.FfmpegExecutorTest"`

Expected: FAIL — `FfmpegExecutor` doesn't exist yet

**Step 3: Implement `FfmpegExecutor`**

In `FfmpegExecutor.kt`:

```kotlin
package net.dinomite.ytpodcast.util

import java.io.InputStream
import org.slf4j.LoggerFactory

/**
 * Executes ffmpeg to convert audio files to MP3 format, streaming the output.
 *
 * The conversion process reads from a file and writes MP3 data to stdout,
 * which is exposed as an InputStream for streaming to clients.
 */
open class FfmpegExecutor {
    private val logger = LoggerFactory.getLogger(FfmpegExecutor::class.java)

    /**
     * Starts an ffmpeg process that converts the input file to MP3, streaming to stdout.
     *
     * The caller is responsible for:
     * - Reading the returned InputStream to completion
     * - Calling [waitFor] after reading to check for errors
     * - Destroying the process if reading is abandoned
     *
     * @param inputFile Path to the raw audio file
     * @return A [ConversionProcess] wrapping the ffmpeg process
     */
    open fun startConversion(inputFile: String): ConversionProcess {
        val command = buildConvertCommand(inputFile)
        logger.debug("Starting ffmpeg: {}", command.joinToString(" "))

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        return ConversionProcess(process)
    }

    companion object {
        fun buildConvertCommand(inputPath: String): List<String> = listOf(
            "ffmpeg",
            "-i", inputPath,
            "-codec:a", "libmp3lame",
            "-q:a", "0",
            "-f", "mp3",
            "pipe:1",
        )
    }
}

/**
 * Wraps a running ffmpeg conversion process.
 *
 * @property process The underlying OS process
 */
class ConversionProcess(private val process: Process) {
    /** The MP3 audio stream (ffmpeg's stdout) */
    val inputStream: InputStream get() = process.inputStream

    /**
     * Waits for the ffmpeg process to complete and checks for errors.
     *
     * @param timeoutMinutes Maximum time to wait
     * @throws FfmpegException if the process times out or exits with non-zero code
     */
    fun waitFor(timeoutMinutes: Long = 10) {
        val completed = process.waitFor(timeoutMinutes, java.util.concurrent.TimeUnit.MINUTES)
        if (!completed) {
            process.destroyForcibly()
            throw FfmpegException("ffmpeg timed out after $timeoutMinutes minutes")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            throw FfmpegException("ffmpeg failed with exit code $exitCode: $stderr")
        }
    }

    /** Forcibly destroys the ffmpeg process. */
    fun destroy() {
        process.destroyForcibly()
    }
}

class FfmpegException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

**Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.FfmpegExecutorTest"`

Expected: PASS

**Step 5: Run all tests and linting**

Run: `./gradlew test && ./gradlew ktlintCheck`

Expected: All PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/FfmpegExecutor.kt \
        src/test/kotlin/net/dinomite/ytpodcast/util/FfmpegExecutorTest.kt
git commit -m "feat: add FfmpegExecutor for streaming audio conversion"
```

---

## Task 2: Change yt-dlp to download raw audio (no conversion)

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt:42-48,161-168`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutorTest.kt:94-105`

Currently `buildDownloadCommand` uses `-x --audio-format mp3 --audio-quality 0`. Change it to use `-f bestaudio` to download raw audio without conversion. Also rename `downloadAudio` → `downloadRawAudio` and the command builder → `buildRawDownloadCommand` to make the intent clear.

**Step 1: Update the command builder test**

In `YtDlpExecutorTest.kt`, replace the existing `buildDownloadCommand` test:

```kotlin
@Test
fun `buildRawDownloadCommand builds correct command`() {
    val command = YtDlpExecutor.buildRawDownloadCommand("dQw4w9WgXcQ", "/tmp/output")

    command shouldBe listOf(
        "yt-dlp",
        "-f", "bestaudio",
        "-o", "/tmp/output",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    )
}
```

**Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.util.YtDlpExecutorTest.buildRawDownloadCommand builds correct command"`

Expected: FAIL — method doesn't exist yet

**Step 3: Update `YtDlpExecutor`**

In `YtDlpExecutor.kt`:

1. Rename `downloadAudio` to `downloadRawAudio` and update to use new command:

```kotlin
open fun downloadRawAudio(videoId: String, outputFile: File) {
    val command = buildRawDownloadCommand(videoId, outputFile.absolutePath)
    executeCommand(command, timeoutMinutes = 10)
    if (!outputFile.exists()) {
        throw YtDlpException("Download completed but output file not found: ${outputFile.absolutePath}")
    }
}
```

2. Replace `buildDownloadCommand` with `buildRawDownloadCommand` in the companion:

```kotlin
fun buildRawDownloadCommand(videoId: String, outputPath: String): List<String> = listOf(
    "yt-dlp",
    "-f", "bestaudio",
    "-o", outputPath,
    "https://www.youtube.com/watch?v=$videoId",
)
```

3. Delete the old `buildDownloadCommand` function.

**Step 4: Update `StubYtDlpExecutor`**

In `src/test/kotlin/net/dinomite/ytpodcast/testsupport/StubYtDlpExecutor.kt`, rename the override:

```kotlin
override fun downloadRawAudio(videoId: String, outputFile: File) {
    val content = audioContent[videoId]
        ?: throw YtDlpException("yt-dlp failed with exit code 1: Video unavailable: $videoId")
    outputFile.writeBytes(content)
}
```

**Step 5: Update `AudioService` to call the renamed method**

In `AudioService.kt`, update `downloadToTempFile`:

```kotlin
fun downloadToTempFile(videoId: String): File {
    val tempFile = File(tempDir, "$videoId.raw")
    ytDlpExecutor.downloadRawAudio(videoId, tempFile)
    return tempFile
}
```

Note the extension changed from `.mp3` to `.raw` since the file is no longer MP3.

**Step 6: Update `AudioServiceTest` to reflect the rename and `.raw` extension**

In `AudioServiceTest.kt`, update the file name assertion:

Change `result.name shouldBe "abc123.mp3"` to `result.name shouldBe "abc123.raw"`.

Also update the mock setup calls from `downloadAudio` to `downloadRawAudio`:

```kotlin
every { ytDlpExecutor.downloadRawAudio("abc123", capture(fileSlot)) } answers { ... }
```

(Apply this to all three tests in the file.)

**Step 7: Run all tests and linting**

Run: `./gradlew test && ./gradlew ktlintCheck`

Expected: All PASS

**Step 8: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt \
        src/main/kotlin/net/dinomite/ytpodcast/services/AudioService.kt \
        src/test/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutorTest.kt \
        src/test/kotlin/net/dinomite/ytpodcast/services/AudioServiceTest.kt \
        src/test/kotlin/net/dinomite/ytpodcast/testsupport/StubYtDlpExecutor.kt
git commit -m "refactor: yt-dlp downloads raw audio without conversion"
```

---

## Task 3: Add `StreamingAudioService` with tee-to-cache logic

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/services/StreamingAudioService.kt`
- Create: `src/test/kotlin/net/dinomite/ytpodcast/services/StreamingAudioServiceTest.kt`

This service orchestrates: download raw → start ffmpeg → stream MP3 to an `OutputStream` while tee-ing to a cache file. It replaces the role of `AudioService` + `CacheService.getAudioFile()` for the cache-miss path.

**Step 1: Write failing tests**

In `StreamingAudioServiceTest.kt`:

```kotlin
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
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.StreamingAudioServiceTest"`

Expected: FAIL — class doesn't exist

**Step 3: Implement `StreamingAudioService`**

In `StreamingAudioService.kt`:

```kotlin
package net.dinomite.ytpodcast.services

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import net.dinomite.ytpodcast.util.FfmpegExecutor
import org.slf4j.LoggerFactory

/**
 * Downloads raw audio via yt-dlp, converts to MP3 via ffmpeg, and streams
 * the result to an OutputStream while simultaneously caching to disk.
 */
class StreamingAudioService(
    private val audioService: AudioService,
    private val ffmpegExecutor: FfmpegExecutor,
    private val cacheDir: String,
) {
    private val logger = LoggerFactory.getLogger(StreamingAudioService::class.java)

    /**
     * Downloads raw audio, converts to MP3, and streams to [outputStream].
     *
     * The converted MP3 is also written to the cache directory. On success,
     * the raw audio file is deleted. On failure, partial cache files are cleaned up.
     *
     * @param videoId The YouTube video ID
     * @param outputStream The stream to write MP3 data to (typically the HTTP response)
     */
    fun streamConvertedAudio(videoId: String, outputStream: OutputStream) {
        val rawFile = audioService.downloadToTempFile(videoId)
        val cacheFile = File(cacheDir, "$videoId.mp3")

        logger.info("Starting streaming conversion: videoId=$videoId, rawFile=${rawFile.name}")
        val conversion = ffmpegExecutor.startConversion(rawFile.absolutePath)

        try {
            FileOutputStream(cacheFile).use { cacheOutputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (conversion.inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    cacheOutputStream.write(buffer, 0, bytesRead)
                }
            }

            conversion.waitFor()
            logger.info("Streaming conversion complete: videoId=$videoId, cacheSize=${cacheFile.length()}")
        } catch (e: Exception) {
            logger.error("Streaming conversion failed: videoId=$videoId", e)
            conversion.destroy()
            cacheFile.delete()
            throw e
        } finally {
            rawFile.delete()
        }
    }
}
```

**Step 4: Run the tests**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.StreamingAudioServiceTest"`

Expected: PASS

**Step 5: Run all tests and linting**

Run: `./gradlew test && ./gradlew ktlintCheck`

Expected: All PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/StreamingAudioService.kt \
        src/test/kotlin/net/dinomite/ytpodcast/services/StreamingAudioServiceTest.kt
git commit -m "feat: add StreamingAudioService for tee-to-cache conversion"
```

---

## Task 4: Update `CacheService` to support streaming path

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt`

Add a method to check if a file is cached (without downloading), and make eviction accessible for the streaming path.

**Step 1: Write the tests**

Add to `CacheServiceTest.kt`:

```kotlin
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
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`

Expected: FAIL — method doesn't exist

**Step 3: Add `getCachedFile` to `CacheService`**

Add to `CacheService.kt`:

```kotlin
/**
 * Returns the cached audio file if it exists, or null if not cached.
 *
 * Updates the file's access time on hit for LRU tracking.
 *
 * @param videoId The YouTube video ID
 * @return The cached file, or null if not in cache
 */
fun getCachedFile(videoId: String): File? {
    val cacheFile = File(config.directory, "$videoId.mp3")
    return if (cacheFile.exists()) {
        logger.info("Cache HIT: videoId=$videoId")
        touchFile(cacheFile)
        cacheFile
    } else {
        null
    }
}
```

Also make `evictIfNeeded()` public so `StreamingAudioService` (or the routing layer) can trigger eviction before a download. Change:

```kotlin
private fun evictIfNeeded() {
```

to:

```kotlin
fun evictIfNeeded() {
```

**Step 4: Run the tests**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`

Expected: PASS

**Step 5: Run all tests and linting**

Run: `./gradlew test && ./gradlew ktlintCheck`

Expected: All PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt \
        src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt
git commit -m "feat: add getCachedFile and expose evictIfNeeded on CacheService"
```

---

## Task 5: Wire up streaming in Routing

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt:28-32,57-60,109-128`
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/Application.kt:21-36`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt:262-286`

Replace the episode handler to use `respondOutputStream` with `StreamingAudioService` on cache miss.

**Step 1: Update `Application.kt` to wire `StreamingAudioService`**

Add the new service creation in `Application.module()`. The module should now create `FfmpegExecutor` and `StreamingAudioService`:

```kotlin
fun Application.module() {
    val appConfig = AppConfig.load(environment.config)
    val cacheConfig = CacheConfig(environment.config, appConfig.cacheDir)

    val ytDlpExecutor = YtDlpExecutor()
    val ffmpegExecutor = FfmpegExecutor()
    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val audioService = AudioService(ytDlpExecutor, appConfig.tempDir)
    val cacheService = CacheService(audioService, cacheConfig)
    val streamingAudioService = StreamingAudioService(audioService, ffmpegExecutor, cacheConfig.directory)

    cacheService.initialize()

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureAuthentication(appConfig)
    configureRouting(appConfig, youTubeMetadataService, cacheService, streamingAudioService)
}
```

**Step 2: Update `configureRouting` signature and `RouteHandlers`**

In `Routing.kt`, add `StreamingAudioService` parameter:

```kotlin
fun Application.configureRouting(
    appConfig: AppConfig,
    youTubeMetadataService: YouTubeMetadataService,
    cacheService: CacheService,
    streamingAudioService: StreamingAudioService,
) {
    val urlBuilder = UrlBuilder(appConfig.baseUrl)
    val rssFeedService = RssFeedService(urlBuilder)

    val handlers = RouteHandlers(youTubeMetadataService, rssFeedService, cacheService, streamingAudioService)
    // ... rest unchanged
}
```

Update `RouteHandlers` constructor:

```kotlin
private class RouteHandlers(
    private val youTubeMetadataService: YouTubeMetadataService,
    private val rssFeedService: RssFeedService,
    private val cacheService: CacheService,
    private val streamingAudioService: StreamingAudioService,
) {
```

**Step 3: Replace `handleEpisodeRequest` with streaming logic**

Replace the existing `handleEpisodeRequest` in `RouteHandlers`:

```kotlin
private suspend fun handleEpisodeRequest(call: ApplicationCall, videoId: String) {
    try {
        // Check cache first
        val cachedFile = cacheService.getCachedFile(videoId)
        if (cachedFile != null) {
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$videoId.mp3\"")
            call.respondFile(cachedFile)
            return
        }

        // Cache miss: stream conversion
        cacheService.evictIfNeeded()
        call.response.header(HttpHeaders.ContentType, ContentType.Audio.MPEG.toString())
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$videoId.mp3\"")
        call.respondOutputStream(contentType = ContentType.Audio.MPEG) {
            streamingAudioService.streamConvertedAudio(videoId, this)
        }
    } catch (e: YtDlpException) {
        logger.error("Failed to download episode $videoId", e)
        respondToYtDlpError(
            call = call,
            exception = e,
            errorConfig = YtDlpErrorConfig(
                notFoundCode = "not_found",
                notFoundMessage = "Video not found: $videoId",
                errorCode = "download_error",
                errorPrefix = "Failed to download episode",
                additionalNotFoundKeywords = listOf("private"),
            ),
        )
    }
}
```

Add `FfmpegException` to the catch chain as well. Add this import at the top of `Routing.kt`:

```kotlin
import net.dinomite.ytpodcast.util.FfmpegException
```

And update the try/catch to also catch `FfmpegException`:

```kotlin
} catch (e: FfmpegException) {
    logger.error("Failed to convert episode $videoId", e)
    call.respond(
        HttpStatusCode.InternalServerError,
        ErrorResponse("conversion_error", "Failed to convert episode: ${e.message}"),
    )
}
```

Note: `FfmpegException` errors during streaming will result in truncated responses (HTTP 200 already sent). This catch handles errors that occur before streaming begins (e.g., during raw download). The `respondOutputStream` catch in Ktor will handle mid-stream errors by closing the connection.

**Step 4: Update `IntegrationTest.testModuleWithStub`**

Add `FfmpegExecutor` and `StreamingAudioService` to the test module helper. Since integration tests use `StubYtDlpExecutor` which writes fake content directly as "MP3", we need a stub `FfmpegExecutor` that acts as a pass-through.

Create a `StubFfmpegExecutor` in the testsupport package:

In `src/test/kotlin/net/dinomite/ytpodcast/testsupport/StubFfmpegExecutor.kt`:

```kotlin
package net.dinomite.ytpodcast.testsupport

import java.io.File
import java.io.FileInputStream
import net.dinomite.ytpodcast.util.ConversionProcess
import net.dinomite.ytpodcast.util.FfmpegExecutor

/**
 * Stub FfmpegExecutor that returns the input file content as-is (no actual conversion).
 */
class StubFfmpegExecutor : FfmpegExecutor() {
    override fun startConversion(inputFile: String): ConversionProcess {
        return StubConversionProcess(File(inputFile))
    }
}
```

Also create a `StubConversionProcess`. Since `ConversionProcess` wraps a `Process`, we need to make the streaming path testable. The simplest approach: make `ConversionProcess` an interface (or open class). However, to avoid over-engineering, we can create a `StubConversionProcess` that extends `ConversionProcess` by passing a mock process.

**Actually, simpler approach:** Since `ConversionProcess` takes a `Process`, and we don't want to mock OS processes, refactor `ConversionProcess` into an interface:

In `FfmpegExecutor.kt`, change `ConversionProcess` to an interface:

```kotlin
interface ConversionProcess {
    val inputStream: java.io.InputStream
    fun waitFor(timeoutMinutes: Long = 10)
    fun destroy()
}
```

And create an implementation class:

```kotlin
class FfmpegConversionProcess(private val process: Process) : ConversionProcess {
    override val inputStream: java.io.InputStream get() = process.inputStream

    override fun waitFor(timeoutMinutes: Long) {
        val completed = process.waitFor(timeoutMinutes, java.util.concurrent.TimeUnit.MINUTES)
        if (!completed) {
            process.destroyForcibly()
            throw FfmpegException("ffmpeg timed out after $timeoutMinutes minutes")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            throw FfmpegException("ffmpeg failed with exit code $exitCode: $stderr")
        }
    }

    override fun destroy() {
        process.destroyForcibly()
    }
}
```

Update `FfmpegExecutor.startConversion` to return `FfmpegConversionProcess(process)`.

Then `StubFfmpegExecutor` can return:

```kotlin
package net.dinomite.ytpodcast.testsupport

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import net.dinomite.ytpodcast.util.ConversionProcess
import net.dinomite.ytpodcast.util.FfmpegExecutor

class StubFfmpegExecutor : FfmpegExecutor() {
    override fun startConversion(inputFile: String): ConversionProcess {
        return object : ConversionProcess {
            override val inputStream: InputStream = FileInputStream(File(inputFile))
            override fun waitFor(timeoutMinutes: Long) {} // No-op
            override fun destroy() {} // No-op
        }
    }
}
```

**Step 5: Update `testModuleWithStub` in `IntegrationTest.kt`**

```kotlin
private fun Application.testModuleWithStub(stubExecutor: StubYtDlpExecutor) {
    val tempDir = System.getProperty("java.io.tmpdir")
    val appConfig = AppConfig(
        baseUrl = "https://test.example.com",
        tempDir = "$tempDir/test-tmp",
        cacheDir = "$tempDir/test-cache",
        authUsername = "testuser",
        authPassword = "testpass",
    )
    val cacheConfig = CacheConfig(
        maxSize = 0L,
        maxCount = 0,
        directory = appConfig.cacheDir
    )
    val stubFfmpegExecutor = StubFfmpegExecutor()
    val youTubeMetadataService = YouTubeMetadataService(stubExecutor)
    val audioService = AudioService(stubExecutor, appConfig.tempDir)
    val cacheService = CacheService(audioService, cacheConfig)
    val streamingAudioService = StreamingAudioService(audioService, stubFfmpegExecutor, cacheConfig.directory)
    cacheService.initialize()

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureAuthentication(appConfig)
    configureRouting(appConfig, youTubeMetadataService, cacheService, streamingAudioService)
}
```

**Step 6: Add required imports to `Routing.kt`**

```kotlin
import io.ktor.server.response.respondOutputStream
import net.dinomite.ytpodcast.services.StreamingAudioService
import net.dinomite.ytpodcast.util.FfmpegException
```

**Step 7: Run all tests and linting**

Run: `./gradlew test && ./gradlew ktlintCheck`

Expected: All PASS

**Step 8: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/Application.kt \
        src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt \
        src/main/kotlin/net/dinomite/ytpodcast/util/FfmpegExecutor.kt \
        src/test/kotlin/net/dinomite/ytpodcast/testsupport/StubFfmpegExecutor.kt \
        src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt
git commit -m "feat: wire streaming MP3 conversion into episode endpoint"
```

---

## Task 6: Remove unused code from old pipeline

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt`

The old `getAudioFile` method in `CacheService` is no longer called — the routing now uses `getCachedFile` + `StreamingAudioService`. Remove it.

**Step 1: Verify `getAudioFile` is no longer referenced**

Search the codebase for `getAudioFile` — it should only appear in `CacheServiceTest.kt` (old tests) and `CacheService.kt` (the method itself). Update `CacheServiceTest` to remove tests that call `getAudioFile`, or keep them if `getAudioFile` is still useful for other callers. If no callers remain, delete the method and its tests.

**Step 2: Run all tests and linting**

Run: `./gradlew test && ./gradlew ktlintCheck`

Expected: All PASS

**Step 3: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt \
        src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt
git commit -m "refactor: remove unused getAudioFile from CacheService"
```

---

## Files Modified Summary

| File | Task | Change |
|------|------|--------|
| `src/main/kotlin/.../util/FfmpegExecutor.kt` | 1, 5 | New: ffmpeg subprocess wrapper with `ConversionProcess` interface |
| `src/test/kotlin/.../util/FfmpegExecutorTest.kt` | 1 | New: command builder test |
| `src/main/kotlin/.../util/YtDlpExecutor.kt` | 2 | Rename `downloadAudio` → `downloadRawAudio`, use `-f bestaudio` |
| `src/test/kotlin/.../util/YtDlpExecutorTest.kt` | 2 | Update command builder test |
| `src/main/kotlin/.../services/AudioService.kt` | 2 | Call `downloadRawAudio`, use `.raw` extension |
| `src/test/kotlin/.../services/AudioServiceTest.kt` | 2 | Update mock calls and file name assertions |
| `src/test/kotlin/.../testsupport/StubYtDlpExecutor.kt` | 2 | Rename override |
| `src/main/kotlin/.../services/StreamingAudioService.kt` | 3 | New: tee-to-cache streaming service |
| `src/test/kotlin/.../services/StreamingAudioServiceTest.kt` | 3 | New: unit tests |
| `src/main/kotlin/.../services/CacheService.kt` | 4, 6 | Add `getCachedFile`, expose `evictIfNeeded`, remove `getAudioFile` |
| `src/test/kotlin/.../services/CacheServiceTest.kt` | 4, 6 | Add/update tests |
| `src/main/kotlin/.../plugins/Routing.kt` | 5 | Streaming episode handler with `respondOutputStream` |
| `src/main/kotlin/.../Application.kt` | 5 | Wire `FfmpegExecutor` + `StreamingAudioService` |
| `src/test/kotlin/.../testsupport/StubFfmpegExecutor.kt` | 5 | New: test stub |
| `src/test/kotlin/.../IntegrationTest.kt` | 5 | Update `testModuleWithStub` |
