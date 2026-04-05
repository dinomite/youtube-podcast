# YouTube to Podcast RSS Feed Converter

A Kotlin/Ktor web service that converts YouTube playlists into podcast RSS feeds with downloadable audio files. It uses `yt-dlp` for metadata and audio extraction, and `ffmpeg` for audio processing.

## Project Overview

- **Core Technology**: Kotlin 2.x, Ktor 3.4+ (Netty engine).
- **External Dependencies**: `yt-dlp`, `ffmpeg`, `deno`.
- **Architecture**:
    - **Plugins**: Ktor feature configurations (Authentication, Routing, Serialization, Monitoring, HTTP).
    - **Services**: Business logic for RSS generation (`RssFeedService`), metadata fetching (`YouTubeMetadataService`), and audio management (`AudioService`, `StreamingAudioService`, `CacheService`).
    - **Caching**: LRU-based file caching for audio files, configurable by size and count.
    - **Security**: HTTP Basic Authentication for protected feeds and audio downloads.

## Building and Running

### Prerequisites
- Java 21+
- `yt-dlp` and `ffmpeg` must be in the system PATH.

### Key Commands
- **Build**: `./gradlew build`
- **Run Locally**:
  ```bash
  AUTH_USERNAME=user AUTH_PASSWORD=pass BASE_DIR=/tmp/ytpodcast ./gradlew run
  ```
- **Test**: `./gradlew test` (Runs JUnit 5 + Kotest tests)
- **Linting**:
    - Check style: `./gradlew ktlintCheck`
    - Fix style: `./gradlew ktlintFormat`
- **Static Analysis**: `./gradlew detekt`

### Docker
- **Build**: `docker build -t youtube-podcast .`
- **Run**: `docker run -p 8080:8080 -e AUTH_USERNAME=user -e AUTH_PASSWORD=pass -e BASE_DIR=/tmp/ytpodcast youtube-podcast`

## Development Conventions

- **Coding Style**: Follows Kotlin coding conventions, enforced by `ktlint`.
- **Naming**:
    - Test functions use backtick descriptive names: `` `should return 200 for health check`() ``.
- **Testing Strategy**:
    - Use `testApplication` for integration tests (`IntegrationTest.kt`).
    - Use stubs for external tools (`StubYtDlpExecutor`, `StubFfmpegExecutor`) in unit/integration tests.
    - Prefer Kotest matchers (`shouldBe`, `shouldContain`).
- **Configuration**:
    - Managed via `src/main/resources/application.conf` (HOCON).
    - Environment variable overrides are supported and preferred for secrets.
    - `AppConfig` and `CacheConfig` are the primary type-safe configuration holders.
- **Error Handling**:
    - Uses Ktor `StatusPages` (in `Monitoring.kt`) for global error handling.
    - Specific yt-dlp errors are mapped to HTTP status codes (404 for missing content, 500 for failures).
- **Dependencies**: Managed in `gradle/libs.versions.toml`.

## API Structure

- **Public**:
    - `GET /`: App info.
    - `GET /health`: Health status.
- **Protected** (Requires HTTP Basic Auth):
    - `GET /show/{playlistId}`: Returns RSS feed (`application/rss+xml`).
    - `GET /episode/{videoId}.mp3`: Returns audio file (`audio/mpeg`).
    - `GET /cache/stats`: Returns JSON with cache usage.
    - `GET /cache/files`: Returns JSON list of cached files.
