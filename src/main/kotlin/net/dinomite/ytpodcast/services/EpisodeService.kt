package net.dinomite.ytpodcast.services

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dinomite.ytpodcast.util.FfmpegExecutor
import org.slf4j.LoggerFactory

/**
 * Orchestrates episode audio retrieval, conversion, and streaming.
 *
 * Uses request coalescing to ensure only one download/conversion is active
 * per videoId at a time.
 */
class EpisodeService(
    private val audioService: AudioService,
    private val cacheService: CacheService,
    private val ffmpegExecutor: FfmpegExecutor,
) {
    private val logger = LoggerFactory.getLogger(EpisodeService::class.java)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Executes the block with a lock specific to the videoId.
     */
    suspend fun <T> withVideoLock(videoId: String, block: suspend () -> T): T =
        locks.computeIfAbsent(videoId) { Mutex() }.withLock {
            block()
        }

    /**
     * Downloads raw audio for a video.
     * Call this before starting the HTTP response so that download errors
     * can be handled with proper status codes.
     */
    fun downloadRawAudio(videoId: String): File {
        cacheService.evictIfNeeded()
        return audioService.downloadToTempFile(videoId)
    }

    /**
     * Converts raw audio to MP3, streaming the result to [outputStream] while
     * simultaneously caching it to disk.
     *
     * @param videoId The YouTube video ID
     * @param rawFile The raw audio file from [downloadRawAudio]
     * @param outputStream The stream to write MP3 data to (e.g. HTTP response)
     */
    fun streamAndCache(videoId: String, rawFile: File, outputStream: OutputStream) {
        val tempCacheFile = cacheService.createTempCacheFile(videoId)

        try {
            logger.info("Starting conversion and caching: videoId=$videoId")
            val conversion = ffmpegExecutor.startConversion(rawFile.absolutePath)

            try {
                FileOutputStream(tempCacheFile).use { cacheOut ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (conversion.inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        cacheOut.write(buffer, 0, bytesRead)
                    }
                }
                conversion.waitFor()
                cacheService.commitTempFile(videoId, tempCacheFile)
                logger.info("Episode successfully cached: videoId=$videoId")
            } catch (e: Exception) {
                logger.error("Streaming conversion failed: videoId=$videoId", e)
                conversion.destroy()
                tempCacheFile.delete()
                throw e
            }
        } finally {
            rawFile.delete()
        }
    }
}
