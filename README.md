# YouTube to Podcast RSS Feed Converter

A Kotlin/Ktor web service that converts YouTube playlists into podcast RSS feeds with downloadable audio files.

## Features

- Generate RSS feeds from YouTube playlists
- Download YouTube videos as MP3 audio files
- iTunes podcast namespace support
- RESTful API endpoints

## Prerequisites

- Java 21 or higher
- yt-dlp CLI tool (for running locally)
- ffmpeg (required by yt-dlp for audio extraction)

## Running Locally

### Build and Run

```bash
./gradlew run
```

The application will start on http://localhost:8080

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
docker run -p 8080:8080 -e BASE_URL=http://localhost:8080 youtube-podcast
```

### Run with Docker Compose

```bash
docker-compose up
```

## API Endpoints

### GET /
Root endpoint - returns application name

### GET /health
Health check endpoint

### GET /show/{playlistId}
Generate RSS feed for a YouTube playlist

**Example:**
```bash
curl http://localhost:8080/show/PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf
```

### GET /episode/{videoId}.mp3
Download audio file for a YouTube video

**Example:**
```bash
curl http://localhost:8080/episode/dQw4w9WgXcQ.mp3 -o episode.mp3
```

## Configuration

The application can be configured via environment variables:

- `PORT` - Server port (default: 8080)
- `BASE_URL` - Base URL for generating episode URLs in RSS feeds (optional)
- `JAVA_OPTS` - JVM options (Docker only, default: `-Xmx512m -Xms256m`)

## Architecture

Built with:
- **Kotlin** - Modern JVM language
- **Ktor 3.4+** - Lightweight web framework
- **Gradle** - Build tool with version catalogs
- **yt-dlp** - YouTube metadata and audio extraction
- **JUnit 5 + Kotest** - Testing framework

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

## License

MIT
