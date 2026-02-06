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
./gradlew run            # Run the application (port 8080)
```

Run a single test:
```bash
./gradlew test --tests "net.dinomite.ytpodcast.ApplicationTest.test root endpoint"
```

## Architecture

**Framework**: Ktor 3.4+ with embedded Netty server on port 8080

**Plugin-based configuration** (`Application.module()` in `Application.kt`):
- `configureSerialization()` - Kotlinx JSON with lenient parsing
- `configureMonitoring()` - Call logging, error handling via StatusPages
- `configureHTTP()` - CORS, default headers
- `configureRouting()` - Endpoint definitions

**Source structure**:
- `plugins/` - Ktor plugin configurations (Serialization, Monitoring, HTTP, Routing, Authentication)
- `plugins/Authentication.kt` - HTTP Basic Auth configuration
- `models/` - Data classes (ErrorResponse, PlaylistMetadata, VideoMetadata)
- `services/` - Business logic services (RssFeedService, AudioService, CacheService, YouTubeMetadataService)
- `util/` - Utilities (YtDlpExecutor, UrlBuilder, SizeParser, YtDlpException)
- `config/` - Application configuration (AppConfig: baseUrl, tempDir; CacheConfig: maxSize, maxCount, directory)

**API Routes** (defined in `plugins/Routing.kt`):
- `GET /` - Root endpoint
- `GET /health` - Health check
- `GET /show/{playlistId}` - RSS feed for a YouTube playlist (content-type: application/rss+xml)
- `GET /episode/{videoId}.mp3` - Audio file for episode (content-type: audio/mpeg)

**Authentication**:
- `/show/{playlistId}` and `/episode/{videoId}.mp3` require HTTP Basic Auth
- `/` and `/health` are public (no authentication required)
- Credentials configured via `application.conf` with `AUTH_USERNAME`/`AUTH_PASSWORD` env var overrides
- Application fails to start if auth credentials are not configured

**Routing Architecture**:
- Single `configureRouting()` function accepting AppConfig, YouTubeMetadataService, and CacheService
- `RouteHandlers` private class encapsulating endpoint logic
- Sophisticated error handling via `YtDlpErrorConfig` - maps yt-dlp errors to HTTP status codes
- Returns 404 for "not found" errors, 500 for other failures

**Service Layer**:
- `YouTubeMetadataService` - Fetches playlist/video metadata via yt-dlp
- `RssFeedService` - Generates RSS XML with iTunes podcast namespace, handles XML escaping, sorting
- `AudioService` - Downloads audio to configured temp directory
- `CacheService` - Manages cached audio files with LRU eviction, enforces size/count limits
- `UrlBuilder` - Intelligent URL generation using AppConfig or request context

## Testing

**Test Structure**:
- `ApplicationTest.kt` - Basic endpoint tests (/, /health, 404, error handling)
- `IntegrationTest.kt` - Full integration tests organized with `@Nested` classes by endpoint:
  - `GetShow` - Tests for `/show/{playlistId}` (success and 404 cases)
  - `GetEpisode` - Tests for `/episode/{videoId}.mp3` (success and 404 cases)
- Unit tests for services (`RssFeedServiceTest`, `AudioServiceTest`, `YouTubeMetadataServiceTest`)
- Unit tests for utilities (`UrlBuilderTest`, `YtDlpExecutorTest`)
- Model tests (`PlaylistMetadataTest`, `VideoMetadataTest`)

**Test Infrastructure**:
- `testsupport/StubYtDlpExecutor.kt` - Test double extending YtDlpExecutor with:
  - `givenPlaylist(id, metadata)` - Configures stub responses for playlist fetches
  - `givenAudio(videoId, content)` - Configures stub responses for audio downloads
  - Throws `YtDlpException` for unconfigured requests
- Uses Ktor's `testApplication` with dependency injection via test helper functions (`testModule()`, `testModuleWithStub()`)
- kotest matchers (`shouldBe`, `shouldContain`) for assertions
- JUnit 5 with `@Nested` classes for test organization

## Code Style

- ktlint with IntelliJ IDEA style, max line length 120
- Detekt for static analysis (config in `detekt.yml`)
- Test functions use backtick naming: `` `test descriptive name`() ``

## Dependencies

Managed via Gradle Version Catalog (`gradle/libs.versions.toml`) using version ranges.
