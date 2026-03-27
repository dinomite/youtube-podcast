# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YouTube to Podcast RSS Feed Converter - a Kotlin/Ktor web service that converts YouTube playlists into podcast feeds of audio files.

## Build Commands

```bash
./gradlew build          # Build project
./gradlew test           # Run all tests
./gradlew ktlintCheck    # Check code style
./gradlew ktlintFormat   # Auto-fix code style issues
./gradlew detekt         # Run static analysis
```

Run a single test:
```bash
./gradlew test --tests "net.dinomite.ytpodcast.ApplicationTest.test root endpoint"
```

## Running the Application

The application requires configuration via `application.conf` with environment variable overrides:

```bash
# Development (uses default credentials from application.conf)
AUTH_USERNAME=dev AUTH_PASSWORD=dev TEMP_DIR=/tmp/ytpodcast ./gradlew run

# Production (with custom credentials)
AUTH_USERNAME=myuser AUTH_PASSWORD=securepass TEMP_DIR=/var/ytpodcast ./gradlew run
```

**Required environment variables:**
- `TEMP_DIR` - Directory for temporary files and cache (e.g., `/tmp/ytpodcast`)

**Optional environment variables with defaults:**
- `AUTH_USERNAME` - Basic auth username (default: "dev" from application.conf)
- `AUTH_PASSWORD` - Basic auth password (default: "dev" from application.conf)
- `BASE_URL` - Base URL for generating episode links (default: "" - uses request host)
- `PORT` - Server port (default: 8080)
- `CACHE_MAX_SIZE` - Maximum cache size (default: "5GB")
- `CACHE_MAX_COUNT` - Maximum number of cached files (default: 100)

The application will fail to start if auth credentials are blank.

## Architecture

**Framework**: Ktor 3.4+ with embedded Netty server on port 8080

**Application Entry Point**: Uses `EngineMain` to bootstrap the server, which loads configuration from `application.conf` (located in `src/main/resources/`). The main function delegates to `EngineMain.main(args)`, which then calls `Application.module()`.

**Configuration**: Loaded from `application.conf` using HOCON format with environment variable substitution via `${?VAR_NAME}` syntax. Configuration is accessed via `environment.config` in the module function and parsed by `AppConfig.load()` and `CacheConfig()`.

**Plugin-based configuration** (`Application.module()` in `Application.kt`):
- `configureSerialization()` - Kotlinx JSON with lenient parsing
- `configureMonitoring()` - Call logging, error handling via StatusPages
- `configureHTTP()` - CORS, default headers
- `configureAuthentication()` - HTTP Basic Auth setup
- `configureRouting()` - Endpoint definitions

**Source structure**:
- `plugins/` - Ktor plugin configurations (Serialization, Monitoring, HTTP, Routing, Authentication)
- `plugins/Authentication.kt` - HTTP Basic Auth configuration
- `models/` - Data classes (ErrorResponse, PlaylistMetadata, VideoMetadata, Thumbnail, CacheFileInfo, CacheStats)
- `services/` - Business logic services (RssFeedService, AudioService, CacheService, YouTubeMetadataService, StreamingAudioService)
- `util/` - Utilities (YtDlpExecutor, YtDlpException, FfmpegExecutor, FfmpegException, UrlBuilder, SizeParser)
- `config/` - Application configuration (AppConfig: baseUrl, baseDir; CacheConfig: maxSize, maxCount, directory)

**API Routes** (defined in `plugins/Routing.kt`):
- `GET /` - Root endpoint (public)
- `GET /health` - Health check (public)
- `GET /show/{playlistId}` - RSS feed for a YouTube playlist (content-type: application/rss+xml)
- `GET /show?url=<youtube-url>` - RSS feed using a full YouTube playlist URL (canonical playlist, watch-with-list, or youtu.be format)
- `GET /episode/{videoId}.mp3` - Audio file for episode (content-type: audio/mpeg)
- `GET /cache/stats` - Cache statistics (CacheStats JSON)
- `GET /cache/files` - List of cached files (CacheFileInfo JSON array)

**Authentication**:
- `/show/{playlistId}`, `/episode/{videoId}.mp3`, `/cache/stats`, and `/cache/files` require HTTP Basic Auth
- `/` and `/health` are public (no authentication required)
- Credentials configured via `application.conf` with `AUTH_USERNAME`/`AUTH_PASSWORD` env var overrides
- Application fails to start if auth credentials are not configured

**Routing Architecture**:
- Single `configureRouting()` function accepting AppConfig, YouTubeMetadataService, CacheService, and StreamingAudioService
- `RouteHandlers` private class encapsulating endpoint logic
- Sophisticated error handling via `YtDlpErrorConfig` - maps yt-dlp errors to HTTP status codes
- Returns 404 for "not found" errors, 500 for other failures
- Episode route checks cache first; on miss, downloads raw audio via yt-dlp then streams ffmpeg conversion to client

**Service Layer**:
- `YouTubeMetadataService` - Fetches playlist/video metadata via yt-dlp
- `RssFeedService` - Generates RSS XML with iTunes podcast namespace, handles XML escaping, sorting
- `AudioService` - Downloads audio to configured temp directory
- `StreamingAudioService` - Downloads raw audio then streams ffmpeg MP3 conversion to client while caching
- `CacheService` - Manages cached audio files with LRU eviction, enforces size/count limits; provides `getStats()` and `listCachedFiles()`
- `UrlBuilder` - Intelligent URL generation using AppConfig or request context

## Testing

**Test Structure**:
- `ApplicationTest.kt` - Basic endpoint tests (/, /health, 404, error handling)
- `ApplicationConfigTest.kt` - Tests for configuration loading from application.conf via EngineMain
- `IntegrationTest.kt` - Full integration tests organized with `@Nested` classes by endpoint:
  - `GetShow` - Tests for `/show/{playlistId}` (success and 404 cases)
  - `GetEpisode` - Tests for `/episode/{videoId}.mp3` (success and 404 cases)
  - `Authentication` - Tests for auth on protected endpoints
- Unit tests for services (`RssFeedServiceTest`, `AudioServiceTest`, `YouTubeMetadataServiceTest`)
- Unit tests for utilities (`UrlBuilderTest`, `YtDlpExecutorTest`)
- Unit tests for config (`AppConfigTest`)
- Model tests (`PlaylistMetadataTest`, `VideoMetadataTest`)

**Test Infrastructure**:
- `testsupport/StubYtDlpExecutor.kt` - Test double extending YtDlpExecutor with:
  - `givenPlaylist(id, metadata)` - Configures stub responses for playlist fetches
  - `givenAudio(videoId, content)` - Configures stub responses for audio downloads
  - Throws `YtDlpException` for unconfigured requests
- `testsupport/StubFfmpegExecutor.kt` - Test double for FfmpegExecutor
- Uses Ktor's `testApplication` with dependency injection via test helper functions (`testModule()`, `testModuleWithStub()`)
- kotest matchers (`shouldBe`, `shouldContain`) for assertions
- JUnit 5 with `@Nested` classes for test organization

## Code Style

- ktlint with IntelliJ IDEA style, max line length 120
- Detekt for static analysis (config in `detekt.yml`)
- Test functions use backtick naming: `` `test descriptive name`() ``

## Dependencies

Managed via Gradle Version Catalog (`gradle/libs.versions.toml`) using version ranges.
