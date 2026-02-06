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
