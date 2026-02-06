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
class AudioService(private val ytDlpExecutor: YtDlpExecutor, private val tempDir: String,) {
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
        val tempFile = File(tempDir, "$videoId.raw")
        ytDlpExecutor.downloadRawAudio(videoId, tempFile)
        return tempFile
    }
}
