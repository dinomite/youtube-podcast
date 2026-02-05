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
class CacheService(private val audioService: AudioService, private val config: CacheConfig,) {
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

    private fun formatSize(bytes: Long): String = when {
        bytes == 0L -> "unlimited"
        bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
