package net.dinomite.ytpodcast.services

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import net.dinomite.ytpodcast.models.PlaylistMetadata
import net.dinomite.ytpodcast.models.VideoMetadata
import net.dinomite.ytpodcast.util.UrlBuilder

/**
 * Generates podcast-compatible RSS feeds from YouTube playlist metadata.
 *
 * The generated RSS feed includes iTunes namespace extensions for
 * compatibility with podcast applications.
 */
class RssFeedService(private val urlBuilder: UrlBuilder) {
    private val rfc2822Formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    /**
     * Generates an RSS feed XML string for the given playlist.
     *
     * @param playlist The playlist metadata to convert to RSS
     * @param scheme Request scheme (https/http) for building episode URLs
     * @param host Request host for building episode URLs
     * @param port Request port for building episode URLs
     * @return The complete RSS feed as an XML string
     */
    fun generateFeed(playlist: PlaylistMetadata, scheme: String, host: String, port: Int): String {
        val sortedEntries = playlist.entries.sortedByDescending { it.uploadDate ?: "" }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/">"""
            )
            appendLine("  <channel>")
            appendLine("    <title>${escapeXml(playlist.title)}</title>")
            appendLine("    <link>https://www.youtube.com/playlist?list=${playlist.id}</link>")
            appendLine("    <description>${escapeXml(playlist.description ?: "")}</description>")
            appendLine("    <language>en</language>")
            playlist.uploader?.let { appendLine("    <itunes:author>${escapeXml(it)}</itunes:author>") }
            playlist.thumbnail?.let { appendLine("""    <itunes:image href="$it"/>""") }
            appendLine("    <itunes:explicit>false</itunes:explicit>")

            for (video in sortedEntries) {
                appendItem(this, video, scheme, host, port)
            }

            appendLine("  </channel>")
            append("</rss>")
        }
    }

    private fun appendItem(builder: StringBuilder, video: VideoMetadata, scheme: String, host: String, port: Int) {
        with(builder) {
            appendLine("    <item>")
            appendLine("      <title>${escapeXml(video.title)}</title>")
            video.description?.let { appendLine("      <description>${escapeXml(it)}</description>") }
            val episodeUrl = urlBuilder.buildEpisodeUrl(video.id, scheme, host, port)
            appendLine("""      <enclosure url="$episodeUrl" type="audio/mpeg" length="0"/>""")
            appendLine("""      <guid isPermaLink="false">${video.id}</guid>""")
            video.uploadDate?.let { appendLine("      <pubDate>${formatPubDate(it)}</pubDate>") }
            video.duration?.let { appendLine("      <itunes:duration>${formatDuration(it)}</itunes:duration>") }
            video.bestThumbnail?.let { appendLine("""      <itunes:image href="$it"/>""") }
            video.description?.let { appendLine("      <itunes:summary>${escapeXml(it)}</itunes:summary>") }
            appendLine("    </item>")
        }
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    private fun formatPubDate(uploadDate: String): String {
        val date = LocalDate.parse(uploadDate, DateTimeFormatter.BASIC_ISO_DATE)
        val dateTime = date.atStartOfDay(ZoneOffset.UTC)
        return rfc2822Formatter.format(dateTime)
    }
}
