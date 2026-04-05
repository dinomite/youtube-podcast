package net.dinomite.ytpodcast.services

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Downloads raw audio for a video. Call this before [streamConversion] so that
     * download errors (e.g. video not found) can be handled before starting the HTTP response.
     *
     * @param videoId The YouTube video ID
     * @return The raw audio file
     */
    fun downloadRawAudio(videoId: String): File = audioService.downloadToTempFile(videoId)

    /**
     * Converts a raw audio file to MP3 and streams to [outputStream] while caching to disk.
     *
     * On success the raw file is deleted and the cache file is kept.
     * On failure the cache file is deleted.
     *
     * @param videoId The YouTube video ID (used for cache file naming)
     * @param rawFile The raw audio file from [downloadRawAudio]
     * @param outputStream The stream to write MP3 data to (typically the HTTP response)
     */
    @Suppress("Detekt:TooGenericExceptionCaught")
    suspend fun streamConversion(videoId: String, rawFile: File, outputStream: OutputStream) {
        val cacheFile = File(cacheDir, "$videoId.mp3")
        if (cacheFile.exists()) {
            logger.info("Cache HIT: Serving already converted file for videoId=$videoId")
            rawFile.delete()
            cacheFile.inputStream().use { it.copyTo(outputStream) }
            return
        }

        val tempCacheFile = File(cacheDir, "$videoId.mp3.tmp-${System.currentTimeMillis()}")
        logger.info("Starting streaming conversion: videoId=$videoId, rawFile=${rawFile.name}")
        val conversion = ffmpegExecutor.startConversion(rawFile.absolutePath)

        try {
            FileOutputStream(tempCacheFile).use { cacheOutputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (conversion.inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    cacheOutputStream.write(buffer, 0, bytesRead)
                }
            }

            conversion.waitFor()
            // Atomic move to final location
            Files.move(tempCacheFile.toPath(), cacheFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            logger.info("Streaming conversion complete: videoId=$videoId, cacheSize=${cacheFile.length()}")
        } catch (e: Exception) {
            logger.error("Streaming conversion failed: videoId=$videoId", e)
            conversion.destroy()
            tempCacheFile.delete()
            throw e
        } finally {
            rawFile.delete()
        }
    }

    /**
     * Executes the provided block while holding a lock for the given [videoId].
     * Callers should use this around [downloadRawAudio] and [streamConversion] to prevent
     * concurrent processing of the same video.
     */
    suspend fun <T> withVideoLock(videoId: String, block: suspend () -> T): T {
        val lock = locks.computeIfAbsent(videoId) { Mutex() }
        return lock.withLock {
            block()
        }
    }

    /**
     * Convenience method that downloads raw audio and streams conversion.
     * Note: errors during download will propagate before any output is written.
     *
     * @param videoId The YouTube video ID
     * @param outputStream The stream to write MP3 data to
     */
    suspend fun streamConvertedAudio(videoId: String, outputStream: OutputStream) {
        withVideoLock(videoId) {
            val cacheFile = File(cacheDir, "$videoId.mp3")
            if (cacheFile.exists()) {
                logger.info("Cache HIT in streamConvertedAudio: videoId=$videoId")
                cacheFile.inputStream().use { it.copyTo(outputStream) }
                return@withVideoLock
            }

            val rawFile = downloadRawAudio(videoId)
            streamConversion(videoId, rawFile, outputStream)
        }
    }
}
