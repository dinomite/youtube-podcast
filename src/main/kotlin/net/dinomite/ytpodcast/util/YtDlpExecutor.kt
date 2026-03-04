package net.dinomite.ytpodcast.util

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import org.slf4j.LoggerFactory

/**
 * Executes yt-dlp CLI commands for fetching YouTube metadata and downloading audio.
 *
 * This class wraps the yt-dlp command-line tool to provide:
 * - Playlist metadata fetching
 * - Video metadata fetching
 * - Audio extraction and download
 */
open class YtDlpExecutor {
    private val logger = LoggerFactory.getLogger(YtDlpExecutor::class.java)

    /**
     * Fetches metadata for a YouTube playlist.
     *
     * @param playlistId The YouTube playlist ID
     * @return Parsed playlist metadata including video entries
     * @throws YtDlpException if the command fails or output cannot be parsed
     */
    open fun fetchPlaylist(playlistId: String): PlaylistMetadata {
        val command = buildPlaylistCommand(playlistId)
        val output = executeCommand(command)
        return parsePlaylistJson(output, jsonParser)
    }

    /**
     * Downloads raw audio from a YouTube video (no conversion).
     *
     * @param videoId The YouTube video ID
     * @param outputFile The file to write the raw audio to
     * @throws YtDlpException if the download fails or output file is not created
     */
    open fun downloadRawAudio(videoId: String, outputFile: File) {
        val command = buildRawDownloadCommand(videoId, outputFile.absolutePath)
        executeCommand(command, timeoutMinutes = 10)
        if (!outputFile.exists()) {
            throw YtDlpException("Download completed but output file not found: ${outputFile.absolutePath}")
        }
    }

    private fun executeCommand(command: List<String>, timeoutMinutes: Long = 2): String {
        logger.debug("Executing: {}", command.joinToString(" "))
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

        if (!completed) {
            process.destroyForcibly()
            throw YtDlpException("Command timed out after $timeoutMinutes minutes")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            logger.error("yt-dlp failed with exit code {}: {}", exitCode, stderr)
            throw YtDlpException("yt-dlp failed with exit code $exitCode: $stderr")
        }

        return stdout
    }

    companion object {
        val jsonParser = Json { ignoreUnknownKeys = true }

        /**
         * Parses yt-dlp --dump-single-json output into playlist metadata.
         *
         * yt-dlp --flat-playlist --dump-single-json returns a single JSON object
         * containing playlist-level fields (id, title, uploader, thumbnail) and
         * an entries array of video objects.
         *
         * @param jsonText The single JSON string from yt-dlp
         * @param json The JSON parser to use
         * @return Parsed playlist metadata
         * @throws YtDlpException if parsing fails
         */
        fun parsePlaylistJson(jsonText: String, json: Json): PlaylistMetadata = try {
            json.decodeFromString<PlaylistMetadata>(jsonText)
        } catch (e: SerializationException) {
            throw YtDlpException("Failed to parse playlist JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw YtDlpException("Failed to parse playlist JSON: ${e.message}", e)
        }

        /**
         * Parses yt-dlp JSON output into video metadata.
         *
         * @param jsonText The JSON string from yt-dlp
         * @param json The JSON parser to use
         * @return Parsed video metadata
         * @throws YtDlpException if parsing fails
         */
        fun parseVideoJson(jsonText: String, json: Json): VideoMetadata = try {
            json.decodeFromString<VideoMetadata>(jsonText)
        } catch (e: SerializationException) {
            throw YtDlpException("Failed to parse video JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw YtDlpException("Failed to parse video JSON: ${e.message}", e)
        }

        /**
         * Builds the yt-dlp command for fetching playlist metadata.
         *
         * @param playlistId The YouTube playlist ID
         * @return Command arguments list
         */
        fun buildPlaylistCommand(playlistId: String): List<String> = listOf(
            "yt-dlp",
            "--flat-playlist",
            "--dump-single-json",
            "https://www.youtube.com/playlist?list=$playlistId",
        )

        /**
         * Builds the yt-dlp command for fetching video metadata.
         *
         * @param videoId The YouTube video ID
         * @return Command arguments list
         */
        fun buildVideoCommand(videoId: String): List<String> = listOf(
            "yt-dlp",
            "--dump-json",
            "https://www.youtube.com/watch?v=$videoId",
        )

        /**
         * Builds the yt-dlp command for downloading raw audio (no conversion).
         *
         * @param videoId The YouTube video ID
         * @param outputPath The output file path
         * @return Command arguments list
         */
        fun buildRawDownloadCommand(videoId: String, outputPath: String): List<String> = listOf(
            "yt-dlp",
            "-f",
            "bestaudio",
            "-o",
            outputPath,
            "https://www.youtube.com/watch?v=$videoId",
        )
    }
}
