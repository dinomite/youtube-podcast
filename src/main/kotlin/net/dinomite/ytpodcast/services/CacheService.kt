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
        evictIfNeeded()
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
                error("Cannot initialize cache")
            }
        }

        if (!cacheDir.canRead() || !cacheDir.canWrite()) {
            logger.error("Cache directory not accessible: ${config.directory}")
            error("Cannot access cache directory")
        }

        logger.info("Cache initialization starting: directory=${config.directory}")
        logger.info("Cache limits: maxSize=${formatSize(config.maxSize)}, maxCount=${config.maxCount}")

        val files = listCacheFiles()
        val totalSize = files.sumOf { it.length() }
        val totalCount = files.size

        logger.info("Cache current state: files=$totalCount, size=${formatSize(totalSize)}")

        evictIfNeeded()

        val postFiles = listCacheFiles()
        val postSize = postFiles.sumOf { it.length() }
        val postCount = postFiles.size
        logger.info("Cache post-cleanup: files=$postCount, size=${formatSize(postSize)}")
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
}
