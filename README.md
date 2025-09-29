# YouTube to Podcast RSS Feed Converter

A Kotlin/Ktor server application that converts YouTube playlists into podcast RSS feeds with MP3 audio episodes.

## Features

- 🎧 Convert YouTube playlists to standard podcast RSS feeds
- 🎵 Automatic audio extraction and MP3 conversion
- 📱 Compatible with all major podcast players
- 💾 Built-in caching for improved performance
- 🔄 Support for HTTP range requests (audio seeking)
- 📊 Comprehensive logging and monitoring
- ✅ Thorough unit and integration tests

## Tech Stack

- **Kotlin** with Coroutines
- **Ktor** - Server framework
- **JavaTube** - YouTube content extraction
- **JAVE2** - Audio conversion to MP3
- **Rome** - RSS feed generation
- **Gradle** - Build system with Kotlin DSL
- **ktlint** - Kotlin linting (IntelliJ IDEA style)
- **Detekt** - Static code analysis
- **JUnit 5** - Testing framework
- **MockK** - Mocking library for tests

## Prerequisites

- JDK 17 or higher
- Gradle 8.0 or higher
- FFmpeg (installed automatically via JAVE2)

## Installation

### Clone the repository
```bash
git clone https://github.com/yourusername/youtube-podcast-converter.git
cd youtube-podcast-converter
```

### Build the project
```bash
./gradlew build
```

### Run tests
```bash
./gradlew test
```

### Run linting and code analysis
```bash
./gradlew ktlintCheck
./gradlew detekt
```

### Fix linting issues automatically
```bash
./gradlew ktlintFormat
```

## Running the Application

### Development mode
```bash
./gradlew run
```

The server will start on `http://localhost:8080`

### Production build
```bash
./gradlew installDist
./build/install/youtube-podcast-converter/bin/youtube-podcast-converter
```

### Docker
```bash
# Build Docker image
docker build -t youtube-podcast-converter .

# Run container
docker run -p 8080:8080 youtube-podcast-converter
```

## API Endpoints

### Health Check
```http
GET /health
```
Returns server health status.

### Convert Playlist to RSS Feed
```http
POST /api/playlist/feed
Content-Type: application/json

{
  "playlistUrl": "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
}
```
Converts a YouTube playlist URL to an RSS feed.

### Get RSS Feed
```http
GET /feed/{playlistId}
```
Retrieves the RSS feed for a specific playlist ID.

### Get Episode Audio
```http
GET /show/{showId}/episodes/{episodeId}.mp3
```
Streams the MP3 audio for a specific episode. Supports HTTP range requests for seeking.

### Get Playlist Information
```http
GET /api/playlist/{playlistId}
```
Returns detailed information about a YouTube playlist.

### Get Video Information
```http
GET /api/video/{videoId}
```
Returns detailed information about a YouTube video.

## Usage Example

1. **Get the RSS feed URL for a YouTube playlist:**
   ```bash
   curl -X POST http://localhost:8080/api/playlist/feed \
     -H "Content-Type: application/json" \
     -d '{"playlistUrl": "YOUR_YOUTUBE_PLAYLIST_URL"}'
   ```

2. **Add the RSS feed to your podcast player:**
   - Copy the feed URL: `http://localhost:8080/feed/{playlistId}`
   - Add it to your favorite podcast app (Apple Podcasts, Spotify, Overcast, etc.)

3. **The podcast player will:**
   - Display the playlist as a podcast
   - Show all videos as episodes
   - Stream audio directly from your server

## Configuration

### Server Configuration
Edit `src/main/kotlin/com/example/ytpodcast/Application.kt`:
```kotlin
embeddedServer(
    Netty,
    port = 8080,        // Change port here
    host = "0.0.0.0",   // Change host here
    module = Application::module
)
```

### Caching Configuration
Modify cache settings in `YouTubeService.kt`:
```kotlin
// Playlist cache (default: 1 hour)
.expireAfterWrite(1, TimeUnit.HOURS)

// Audio cache (default: 30 minutes, 500MB max)
.expireAfterWrite(30, TimeUnit.MINUTES)
.maximumWeight(500_000_000) // 500MB
```

### Audio Quality
Adjust MP3 bitrate in `AudioConverterService.kt`:
```kotlin
audioAttributes.setBitRate(128000) // 128 kbps (default)
```

## Project Structure

```
src/
├── main/
│   ├── kotlin/com/example/ytpodcast/
│   │   ├── Application.kt           # Main application entry
│   │   ├── models/                  # Data models
│   │   │   └── Models.kt
│   │   ├── plugins/                 # Ktor plugins
│   │   │   ├── HTTP.kt
│   │   │   ├── Monitoring.kt
│   │   │   ├── Routing.kt
│   │   │   └── Serialization.kt
│   │   └── service/                 # Business logic
│   │       ├── YouTubeService.kt    # YouTube interaction
│   │       ├── AudioConverterService.kt # Audio conversion
│   │       └── RssFeedService.kt    # RSS generation
│   └── resources/
│       └── logback.xml              # Logging configuration
└── test/
    └── kotlin/com/example/ytpodcast/
        ├── ApplicationTest.kt        # Integration tests
        └── service/                  # Unit tests
            ├── YouTubeServiceTest.kt
            ├── AudioConverterServiceTest.kt
            └── RssFeedServiceTest.kt
```

## Docker Deployment

### Dockerfile
```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY build/install/youtube-podcast-converter/ /app/

EXPOSE 8080

CMD ["./bin/youtube-podcast-converter"]
```

### docker-compose.yml
```yaml
version: '3.8'

services:
  youtube-podcast:
    build: .
    ports:
      - "8080:8080"
    environment:
      - JAVA_OPTS=-Xmx2G -Xms512M
    volumes:
      - ./logs:/app/logs
    restart: unless-stopped
```

## Performance Optimization

1. **Caching:** The application caches playlist information and audio data to reduce YouTube API calls
2. **Coroutines:** Asynchronous processing for better scalability
3. **Streaming:** Audio is streamed rather than fully loaded into memory
4. **Range Requests:** Support for partial content requests enables efficient seeking

## Monitoring and Logging

- Logs are written to both console and files in the `logs/` directory
- Daily log rotation is configured
- Request/response logging for API endpoints
- Detailed error logging with stack traces

## Testing

The project includes comprehensive tests:
- Unit tests for all services
- Integration tests for API endpoints
- Mock tests for external dependencies

Run tests with coverage:
```bash
./gradlew test jacocoTestReport
```

## Limitations

- YouTube's Terms of Service should be reviewed before deploying
- Audio quality depends on source video quality
- Large playlists may take time to process initially
- Rate limiting may apply for YouTube requests

## Troubleshooting

### FFmpeg not found
JAVE2 includes FFmpeg binaries for major platforms. If issues occur, install FFmpeg manually:
```bash
# Ubuntu/Debian
sudo apt-get install ffmpeg

# macOS
brew install ffmpeg

# Windows
# Download from https://ffmpeg.org/download.html
```

### OutOfMemoryError
Increase JVM heap size:
```bash
export JAVA_OPTS="-Xmx4G -Xms1G"
./gradlew run
```

### Playlist not loading
- Verify the playlist is public
- Check if the playlist URL is correct
- Review logs for detailed error messages

## Contributing

1. Fork the repository
2. Create a feature branch
3. Run tests and linting
4. Submit a pull request

## License

This project is provided for educational purposes. Please ensure compliance with YouTube's Terms of Service and respect content creators' rights when using this application.

## Disclaimer

This tool is for personal use only. Users are responsible for complying with YouTube's Terms of Service and all applicable laws regarding content usage and distribution.