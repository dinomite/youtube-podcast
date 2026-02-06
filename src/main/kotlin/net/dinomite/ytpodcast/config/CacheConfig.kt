package net.dinomite.ytpodcast.config

import io.ktor.server.config.ApplicationConfig
import net.dinomite.ytpodcast.util.parseSize

/**
 * Configuration for the audio file cache.
 *
 * @property maxSize Maximum cache size in bytes (0 = unlimited)
 * @property maxCount Maximum number of cached files (0 = unlimited)
 * @property directory Directory path where cache files are stored
 */
data class CacheConfig(val maxSize: Long, val maxCount: Int, val directory: String) {
    constructor(config: ApplicationConfig, cacheDir: String) : this(
        maxSize = parseSize(config.propertyOrNull("ytpodcast.cache.maxSize")?.getString() ?: "5GB"),
        maxCount = config.propertyOrNull("ytpodcast.cache.maxCount")?.getString()?.toInt() ?: 100,
        directory = cacheDir
    )
}
