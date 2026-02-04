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
- `plugins/` - Ktor plugin configurations (Serialization, Monitoring, HTTP, Routing)
- `models/` - Data classes (DTOs)

**API Routes** (defined in `plugins/Routing.kt`):
- `GET /` - Root endpoint
- `GET /health` - Health check
- `GET /show/{playlistId}` - RSS feed for a YouTube playlist
- `GET /episode/{videoId}.mp3` - Audio file for episode

## Testing

Uses Ktor's `testApplication` for integration testing with kotest matchers (`shouldBe`, `shouldContain`). Tests configure the application module identically to production via `testModule()`.

## Code Style

- ktlint with IntelliJ IDEA style, max line length 120
- Detekt for static analysis (config in `detekt.yml`)
- Test functions use backtick naming: `` `test descriptive name`() ``

## Dependencies

Managed via Gradle Version Catalog (`gradle/libs.versions.toml`) using version ranges.
