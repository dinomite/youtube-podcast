package net.dinomite.ytpodcast.util

import java.net.URI

/**
 * Extracts the YouTube playlist ID (the `list` query parameter) from a YouTube URL.
 *
 * Supports:
 * - https://www.youtube.com/playlist?list=PLxxxxxx
 * - https://www.youtube.com/watch?v=xxxxxx&list=PLxxxxxx
 * - https://youtu.be/xxxxxx?list=PLxxxxxx
 *
 * @return the playlist ID, or null if the URL is malformed or has no `list` param
 */
fun extractPlaylistId(url: String): String? = runCatching {
    URI(url).query
        ?.split("&")
        ?.firstOrNull { it.startsWith("list=") }
        ?.removePrefix("list=")
        ?.takeIf { it.isNotEmpty() }
}.getOrNull()
