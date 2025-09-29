package com.example.ytpodcast.service

import com.example.ytpodcast.models.PlaylistInfo
import com.rometools.rome.feed.synd.*
import com.rometools.rome.io.SyndFeedOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.time.Instant
import java.util.*

class RssFeedService {
    private val logger = LoggerFactory.getLogger(RssFeedService::class.java)
    
    suspend fun generatePodcastFeed(
        playlist: PlaylistInfo,
        baseUrl: String
    ): String = withContext(Dispatchers.IO) {
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
            playlist.thumbnailUrl?.let { thumbnailUrl ->
                feed.image = SyndImageImpl().apply {
                    title = playlist.title
                    url = thumbnailUrl
                    link = "$baseUrl/feed/${playlist.id}"
                }
            }
            
            val output = SyndFeedOutput()
            val writer = StringWriter()
            output.output(feed, writer)
            
            logger.info("Generated RSS feed for playlist ${playlist.id}")
            
            writer.toString()
        } catch (e: Exception) {
            logger.error("Failed to generate RSS feed for playlist ${playlist.id}", e)
            throw RuntimeException("RSS feed generation failed: ${e.message}", e)
        }
    }
    
    private fun createEpisodeEntry(
        video: com.example.ytpodcast.models.VideoInfo,
        playlistId: String,
        baseUrl: String,
        episodeNumber: Int
    ): SyndEntry {
        return SyndEntryImpl().apply {
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
    }
    
    private fun createITunesModule(playlist: PlaylistInfo): com.rometools.modules.itunes.FeedInformation {
        return object : com.rometools.modules.itunes.FeedInformation {
            override fun getUri(): String = com.rometools.modules.itunes.ITunes.URI
            
            override fun getAuthor(): String? = playlist.author
            
            override fun getBlock(): Boolean = false
            
            override fun setBlock(block: Boolean) {}
            
            override fun getCategories(): List<com.rometools.modules.itunes.types.Category> = emptyList()
            
            override fun setCategories(categories: List<com.rometools.modules.itunes.types.Category>) {}
            
            override fun getComplete(): Boolean = false
            
            override fun setComplete(complete: Boolean) {}
            
            override fun getDuration(): com.rometools.modules.itunes.types.Duration? = null
            
            override fun setDuration(duration: com.rometools.modules.itunes.types.Duration?) {}
            
            override fun getExplicit(): Boolean = false
            
            override fun setExplicit(explicit: Boolean) {}
            
            override fun getImage(): java.net.URL? = playlist.thumbnailUrl?.let {
                try {
                    java.net.URL(it)
                } catch (e: Exception) {
                    null
                }
            }
            
            override fun setImage(image: java.net.URL?) {}
            
            override fun getKeywords(): Array<String> = emptyArray()
            
            override fun setKeywords(keywords: Array<String>) {}
            
            override fun getNewFeedUrl(): java.net.URL? = null
            
            override fun setNewFeedUrl(url: java.net.URL?) {}
            
            override fun getOwner(): String? = playlist.author
            
            override fun setOwner(owner: String?) {}
            
            override fun getOwnerEmailAddress(): String? = null
            
            override fun setOwnerEmailAddress(email: String?) {}
            
            override fun getOwnerName(): String? = playlist.author
            
            override fun setOwnerName(name: String?) {}
            
            override fun getSubtitle(): String? = playlist.description
            
            override fun setSubtitle(subtitle: String?) {}
            
            override fun getSummary(): String? = playlist.description
            
            override fun setSummary(summary: String?) {}
            
            override fun getType(): String = "episodic"
            
            override fun setType(type: String?) {}
            
            override fun setAuthor(author: String?) {}
            
            override fun clone(): Any = this
        }
    }
    
    private fun createITunesEntryModule(
        video: com.example.ytpodcast.models.VideoInfo,
        episodeNumber: Int
    ): com.rometools.modules.itunes.EntryInformation {
        return object : com.rometools.modules.itunes.EntryInformation {
            override fun getUri(): String = com.rometools.modules.itunes.ITunes.URI
            
            override fun getAuthor(): String? = video.author
            
            override fun getBlock(): Boolean = false
            
            override fun setBlock(block: Boolean) {}
            
            override fun getDuration(): com.rometools.modules.itunes.types.Duration? = 
                video.duration?.let {
                    com.rometools.modules.itunes.types.Duration(it * 1000) // Convert seconds to milliseconds
                }
            
            override fun setDuration(duration: com.rometools.modules.itunes.types.Duration?) {}
            
            override fun getExplicit(): Boolean = false
            
            override fun setExplicit(explicit: Boolean) {}
            
            override fun getImage(): java.net.URL? = video.thumbnailUrl?.let {
                try {
                    java.net.URL(it)
                } catch (e: Exception) {
                    null
                }
            }
            
            override fun setImage(image: java.net.URL?) {}
            
            override fun getKeywords(): Array<String> = emptyArray()
            
            override fun setKeywords(keywords: Array<String>) {}
            
            override fun getSubtitle(): String? = video.description?.take(255)
            
            override fun setSubtitle(subtitle: String?) {}
            
            override fun getSummary(): String? = video.description
            
            override fun setSummary(summary: String?) {}
            
            override fun getOrder(): Int = episodeNumber
            
            override fun setOrder(order: Int?) {}
            
            override fun setAuthor(author: String?) {}
            
            override fun getClosedCaptioned(): Boolean = false
            
            override fun setClosedCaptioned(closedCaptioned: Boolean?) {}
            
            override fun getEpisode(): Int? = episodeNumber
            
            override fun setEpisode(episode: Int?) {}
            
            override fun getEpisodeType(): String = "full"
            
            override fun setEpisodeType(episodeType: String?) {}
            
            override fun getSeason(): Int? = null
            
            override fun setSeason(season: Int?) {}
            
            override fun clone(): Any = this
        }
    }
}