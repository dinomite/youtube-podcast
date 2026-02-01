package net.dinomite.ytpodcast.service

import com.rometools.modules.itunes.EntryInformationImpl
import com.rometools.modules.itunes.FeedInformationImpl
import com.rometools.modules.itunes.types.Duration
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEnclosureImpl
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.io.SyndFeedOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dinomite.ytpodcast.models.PlaylistInfo
import net.dinomite.ytpodcast.models.ResponseVideoInfo
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.net.URL
import java.util.Date

@Suppress("Detekt:TooGenericExceptionCaught")
class RssFeedService {
    private val logger = LoggerFactory.getLogger(RssFeedService::class.java)

    suspend fun generatePodcastFeed(playlist: PlaylistInfo, baseUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val feed = SyndFeedImpl().apply {
                feedType = "rss_2.0"
                title = playlist.title
                link = "$baseUrl/feed/${playlist.id}"
                description = playlist.description ?: "YouTube playlist converted to podcast"
                author = playlist.author ?: "Unknown"
                publishedDate = Date()

                // Add iTunes podcast namespace
                modules = mutableListOf(
                    createITunesModule(playlist)
                )

                // Add episodes
                entries = playlist.videos.mapIndexed { index, video ->
                    createEpisodeEntry(video, playlist.id, baseUrl, index + 1)
                }
            }

            // Add podcast image if available
//            playlist.thumbnailUrl?.let { thumbnailUrl ->
//                feed.image = SyndImageImpl().apply {
//                    title = playlist.title
//                    url = thumbnailUrl
//                    link = "$baseUrl/feed/${playlist.id}"
//                }
//            }

            val output = SyndFeedOutput()
            val writer = StringWriter()
            output.output(feed, writer)

            logger.info("Generated RSS feed for playlist ${playlist.id}")

            writer.toString()
        } catch (e: Exception) {
            logger.error("RSS feed generation failed: ${e.message}", e)
            throw IllegalStateException("RSS feed generation failed: ${e.message}", e)
        }
    }

    private fun createEpisodeEntry(
        video: ResponseVideoInfo,
        playlistId: String,
        baseUrl: String,
        episodeNumber: Int
    ): SyndEntry = SyndEntryImpl().apply {
        title = video.title
        link = "$baseUrl/show/$playlistId/episodes/${video.id}.mp3"
        description = SyndContentImpl().apply {
            type = "text/plain"
            value = video.description ?: "Episode $episodeNumber"
        }
        author = video.author ?: "Unknown"
        publishedDate = video.publishedAt?.let { Date(it * 1000) } ?: Date()
        uri = video.id

        // Add enclosure for podcast players
        enclosures = listOf(
            SyndEnclosureImpl().apply {
                url = "$baseUrl/show/$playlistId/episodes/${video.id}.mp3"
                type = "audio/mpeg"
                // Set a default length if duration is not available
                length = video.duration?.times(128000)?.div(8) ?: 10000000 // Approximate size based on bitrate
            }
        )

        // Add iTunes episode information
        modules = mutableListOf(
            createITunesEntryModule(video, episodeNumber)
        )
    }

    private fun createITunesModule(playlist: PlaylistInfo): FeedInformationImpl = FeedInformationImpl().apply {
        author = playlist.author
        subtitle = playlist.description
        summary = playlist.description

//        playlist.thumbnailUrl?.let { thumbnailUrl ->
//            try {
//                image = URL(thumbnailUrl)
//            } catch (e: Exception) {
//                logger.warn("Invalid thumbnail URL: $thumbnailUrl", e)
//            }
//        }

        ownerName = playlist.author
        type = "episodic"
    }

    private fun createITunesEntryModule(video: ResponseVideoInfo, episodeNumber: Int): EntryInformationImpl =
        EntryInformationImpl().apply {
            author = video.author
            subtitle = video.description?.take(255)
            summary = video.description

            video.duration?.let {
                duration = Duration(it * 1000) // Convert seconds to milliseconds
            }

            video.thumbnailUrl?.let { thumbnailUrl ->
                try {
                    image = URL(thumbnailUrl)
                } catch (e: Exception) {
                    logger.warn("Invalid thumbnail URL: $thumbnailUrl", e)
                }
            }

            order = episodeNumber
            episode = episodeNumber
            episodeType = "full"
        }
}
