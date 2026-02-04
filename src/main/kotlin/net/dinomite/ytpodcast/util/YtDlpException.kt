package net.dinomite.ytpodcast.util

/**
 * Exception thrown when yt-dlp operations fail.
 *
 * @property message Description of what went wrong
 * @property cause The underlying exception, if any
 */
class YtDlpException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
