package net.dinomite.ytpodcast.util

/**
 * Builds URLs for podcast episodes.
 *
 * Uses the configured base URL if provided, otherwise constructs
 * URLs from request information (scheme, host, port).
 */
class UrlBuilder(private val configuredBaseUrl: String) {
    /**
     * Builds the URL for an episode's audio file.
     *
     * @param videoId The YouTube video ID
     * @param scheme Request scheme (https/http), used when no configured base URL
     * @param host Request host, used when no configured base URL
     * @param port Request port, used when no configured base URL
     * @return The full URL to the episode's MP3 file
     */
    fun buildEpisodeUrl(videoId: String, scheme: String = "", host: String = "", port: Int = 0,): String {
        val base = resolveBaseUrl(scheme, host, port)
        return "$base/episode/$videoId.mp3"
    }

    private fun resolveBaseUrl(scheme: String, host: String, port: Int): String {
        if (configuredBaseUrl.isNotEmpty()) {
            return configuredBaseUrl.trimEnd('/')
        }

        val portSuffix = when {
            scheme == "https" && port == 443 -> ""
            scheme == "http" && port == 80 -> ""
            else -> ":$port"
        }
        return "$scheme://$host$portSuffix"
    }
}
