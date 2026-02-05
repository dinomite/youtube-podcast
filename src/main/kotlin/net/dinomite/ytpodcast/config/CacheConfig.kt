package net.dinomite.ytpodcast.config

/**
 * Configuration for the audio file cache.
 *
 * @property maxSize Maximum cache size in bytes (0 = unlimited)
 * @property maxCount Maximum number of cached files (0 = unlimited)
 * @property directory Directory path where cache files are stored
 */
data class CacheConfig(val maxSize: Long, val maxCount: Int, val directory: String,)
