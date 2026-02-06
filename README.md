# YouTube to Podcast RSS Feed Converter

A Kotlin/Ktor web service that converts YouTube playlists into podcast RSS feeds with downloadable audio files.

## Features

- Generate RSS feeds from YouTube playlists
- Download YouTube videos as MP3 audio files
- iTunes podcast namespace support
- RESTful API endpoints
- HTTP Basic Authentication to protect feeds and audio files
- Configurable via application.conf with environment variable overrides

## Prerequisites

- Java 21 or higher
- yt-dlp CLI tool (for running locally)
- ffmpeg (required by yt-dlp for audio extraction)
- Deno (required by yt-dlp for YouTube extraction)

## Running Locally

### Build and Run

```bash
# Development mode with default credentials
AUTH_USERNAME=dev AUTH_PASSWORD=dev TEMP_DIR=/tmp/ytpodcast ./gradlew run

# Production mode with custom credentials
AUTH_USERNAME=myuser AUTH_PASSWORD=securepass TEMP_DIR=/var/ytpodcast ./gradlew run
```

The application will start on http://localhost:8080

**Note:** Authentication credentials and temp directory are required. The application will fail to start if auth credentials are not configured.

### Run Tests

```bash
./gradlew test
```

### Code Quality

```bash
./gradlew ktlintCheck    # Check code style
./gradlew ktlintFormat   # Auto-fix code style
./gradlew detekt         # Run static analysis
```

## Running with Docker

### Build the Docker Image

```bash
docker build -t youtube-podcast .
```

### Run with Docker

```bash
docker run -p 8080:8080 \
  -e AUTH_USERNAME=dev \
  -e AUTH_PASSWORD=dev \
  -e TEMP_DIR=/tmp/ytpodcast \
  -e BASE_URL=http://localhost:8080 \
  youtube-podcast
```

### Run with Docker Compose

The `docker-compose.yml` includes default configuration for authentication and temp directory:

```bash
docker-compose up
```

**Security Note:** For production deployments, override the default credentials by setting `AUTH_USERNAME` and `AUTH_PASSWORD` environment variables or by editing the docker-compose.yml file.

## API Endpoints

### Public Endpoints (No Authentication Required)

#### GET /
Root endpoint - returns application name

**Example:**
```bash
curl http://localhost:8080/
```

#### GET /health
Health check endpoint

**Example:**
```bash
curl http://localhost:8080/health
```

### Protected Endpoints (Require HTTP Basic Auth)

#### GET /show/{playlistId}
Generate RSS feed for a YouTube playlist

**Example:**
```bash
curl -u dev:dev http://localhost:8080/show/PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf
```

#### GET /episode/{videoId}.mp3
Download audio file for a YouTube video

**Example:**
```bash
curl -u dev:dev http://localhost:8080/episode/dQw4w9WgXcQ.mp3 -o episode.mp3
```

### Using with Podcast Clients

Most podcast clients support HTTP Basic Authentication. Configure your feed URL with embedded credentials:

```
http://username:password@localhost:8080/show/PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf
```

## Configuration

The application is configured via `application.conf` (HOCON format) with environment variable overrides.

### Required Environment Variables

- `TEMP_DIR` - Directory for cached audio files (e.g., `/tmp/ytpodcast`)

### Authentication

Authentication credentials can be set via environment variables or use defaults from `application.conf`:

- `AUTH_USERNAME` - HTTP Basic Auth username (default: "dev")
- `AUTH_PASSWORD` - HTTP Basic Auth password (default: "dev")

**Security Note:** Always use strong credentials in production and never commit credentials to version control.

See [docs/authentication.md](docs/authentication.md) for detailed authentication documentation.

### Optional Environment Variables

- `PORT` - Server port (default: 8080)
- `BASE_URL` - Base URL for generating episode URLs in RSS feeds (default: uses request host)
- `JAVA_OPTS` - JVM options (Docker only, default: `-Xmx512m -Xms256m`)
- `CACHE_MAX_SIZE` - Maximum cache size (default: "5GB", "0" for unlimited)
- `CACHE_MAX_COUNT` - Maximum number of cached files (default: 100, 0 for unlimited)

### Cache Management

Downloaded audio files are cached to improve performance. The cache uses LRU (Least Recently Used) eviction when limits are reached:

- Size and count limits are enforced simultaneously
- Least recently accessed files are deleted first
- Cache is cleaned up on startup and before each download
- Set limits to "0" to disable that constraint

## Architecture

Built with:
- **Kotlin** - Modern JVM language
- **Ktor 3.4+** - Lightweight web framework
- **Gradle** - Build tool with version catalogs
- **yt-dlp** - YouTube metadata and audio extraction
- **JUnit 5 + Kotest** - Testing framework

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.
