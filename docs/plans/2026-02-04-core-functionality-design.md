# Core Functionality Design

YouTube playlist to podcast RSS feed converter.

## Decisions

| Aspect | Decision |
|--------|----------|
| YouTube data source | yt-dlp (metadata + audio) |
| State | Stateless, no caching |
| Audio delivery | On-demand, buffer to temp file first |
| Base URL | Config file, env var, or request headers |
| RSS format | Full iTunes/podcast namespace |
| Errors | HTTP 404/500 responses |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Ktor Application                      │
├─────────────────────────────────────────────────────────┤
│  Routes:                                                 │
│    GET /show/{playlistId}      → RssFeedService         │
│    GET /episode/{videoId}.mp3  → AudioService           │
├─────────────────────────────────────────────────────────┤
│  Services:                                               │
│    YouTubeMetadataService  - Playlist/video metadata    │
│    RssFeedService          - Generates podcast XML      │
│    AudioService            - Downloads & converts audio │
├─────────────────────────────────────────────────────────┤
│  Utilities:                                              │
│    YtDlpExecutor           - Wraps yt-dlp CLI calls     │
│    UrlBuilder              - Constructs episode URLs    │
└─────────────────────────────────────────────────────────┘
```

### Request Flow: RSS Feed

1. Client requests `/show/{playlistId}`
2. `YouTubeMetadataService` calls yt-dlp to fetch playlist info
3. `RssFeedService` transforms metadata into RSS XML with iTunes extensions
4. Episode URLs point to `/episode/{videoId}.mp3`

### Request Flow: Episode Audio

1. Client requests `/episode/{videoId}.mp3`
2. `AudioService` calls yt-dlp to download and convert to MP3 in temp file
3. Response streams temp file with `Content-Type` and `Content-Length`
4. Temp file deleted after response completes

## yt-dlp Integration

### Commands

```bash
# Fetch playlist metadata (no download)
yt-dlp --flat-playlist --dump-json "https://www.youtube.com/playlist?list={playlistId}"

# Fetch single video metadata (no download)
yt-dlp --dump-json "https://www.youtube.com/watch?v={videoId}"

# Download and convert to MP3
yt-dlp -x --audio-format mp3 --audio-quality 0 -o "{tempDir}/{videoId}.mp3" "https://www.youtube.com/watch?v={videoId}"
```

### Field Mapping

| YouTube Field | Podcast Use |
|---------------|-------------|
| `playlist_title` | Feed title |
| `playlist_description` | Feed description |
| `title` | Episode title |
| `description` | Episode description / `itunes:summary` |
| `thumbnail` | Feed/episode artwork (`itunes:image`) |
| `duration` | `itunes:duration` |
| `upload_date` | `pubDate` |
| `uploader` | `itunes:author` |
| `id` | Used in episode URL |

## RSS Feed Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:content="http://purl.org/rss/1.0/modules/content/">
  <channel>
    <title>{playlist_title}</title>
    <link>https://www.youtube.com/playlist?list={playlistId}</link>
    <description>{playlist_description}</description>
    <language>en</language>
    <itunes:author>{uploader}</itunes:author>
    <itunes:image href="{playlist_thumbnail}"/>
    <itunes:explicit>false</itunes:explicit>

    <item>
      <title>{video_title}</title>
      <description>{video_description}</description>
      <enclosure url="{baseUrl}/episode/{videoId}.mp3"
                 type="audio/mpeg" length="0"/>
      <guid isPermaLink="false">{videoId}</guid>
      <pubDate>{upload_date as RFC-2822}</pubDate>
      <itunes:duration>{duration in HH:MM:SS}</itunes:duration>
      <itunes:image href="{video_thumbnail}"/>
      <itunes:summary>{video_description}</itunes:summary>
    </item>
  </channel>
</rss>
```

Notes:
- `enclosure length="0"` since file size unknown without converting
- `guid` uses videoId for uniqueness and stability
- Episodes ordered by upload date (newest first)

## Configuration

### application.conf

```hocon
ktor {
    deployment {
        port = 8080
    }
    application {
        modules = [ net.dinomite.ytpodcast.ApplicationKt.module ]
    }
}

ytpodcast {
    baseUrl = ""  # Empty = derive from request headers
    baseUrl = ${?BASE_URL}  # Override via environment variable
}
```

### URL Building Precedence

1. `BASE_URL` environment variable (highest)
2. `ytpodcast.baseUrl` in application.conf
3. Request headers (fallback)

## Audio Service

```kotlin
suspend fun streamEpisode(videoId: String, call: ApplicationCall) {
    val tempFile = createTempFile(prefix = "yt-", suffix = ".mp3")
    try {
        ytDlpExecutor.downloadAudio(videoId, tempFile)
        call.response.header(HttpHeaders.ContentDisposition,
            "attachment; filename=\"$videoId.mp3\"")
        call.respondFile(tempFile)
    } finally {
        tempFile.delete()
    }
}
```

### Error Handling

- Video unavailable → HTTP 404
- yt-dlp not installed → HTTP 500
- Conversion fails → HTTP 500 with error details in logs

## Project Structure

```
src/main/kotlin/net/dinomite/ytpodcast/
├── Application.kt
├── config/
│   └── AppConfig.kt
├── models/
│   ├── ErrorResponse.kt
│   ├── PlaylistMetadata.kt
│   └── VideoMetadata.kt
├── plugins/
│   └── Routing.kt
├── services/
│   ├── AudioService.kt
│   ├── RssFeedService.kt
│   └── YouTubeMetadataService.kt
└── util/
    ├── UrlBuilder.kt
    └── YtDlpExecutor.kt

src/test/kotlin/net/dinomite/ytpodcast/
├── ApplicationTest.kt
├── models/
│   ├── PlaylistMetadataTest.kt
│   └── VideoMetadataTest.kt
├── services/
│   ├── AudioServiceTest.kt
│   ├── RssFeedServiceTest.kt
│   └── YouTubeMetadataServiceTest.kt
└── util/
    ├── UrlBuilderTest.kt
    └── YtDlpExecutorTest.kt
```
