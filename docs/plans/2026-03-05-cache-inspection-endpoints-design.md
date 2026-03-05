# Cache Inspection Endpoints Design

## Overview

Add two authenticated endpoints for inspecting the state of the audio file cache:
- `GET /cache/stats` — overall cache statistics with limits
- `GET /cache/files` — list of cached files with metadata

## Authentication

Both endpoints require HTTP Basic Auth via the existing `"podcast-auth"` mechanism, consistent with `/show` and `/episode`.

## Data Models

Two new data classes in `models/`:

**`CacheStats`**
```kotlin
data class CacheStats(
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val maxFiles: Int,       // 0 = unlimited
    val maxSizeBytes: Long,  // 0 = unlimited
)
```

**`CacheFileInfo`**
```kotlin
data class CacheFileInfo(
    val videoId: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
)
```

## CacheService Changes

Add two public methods to `CacheService`:

- `getStats(): CacheStats` — lists cache files, sums sizes, reads limits from `config`
- `listFiles(): List<CacheFileInfo>` — maps each `.mp3` file to `CacheFileInfo`, extracting `videoId` by stripping the `.mp3` extension

The existing private `listCacheFiles()` method provides the underlying file enumeration for both.

## Routing Changes

In `Routing.kt`, register two new routes inside the existing `authenticate("podcast-auth")` block via new methods on `RouteHandlers`:

- `GET /cache/stats` → calls `cacheService.getStats()`, responds with JSON
- `GET /cache/files` → calls `cacheService.listFiles()`, responds with JSON array

## Example Responses

`GET /cache/stats`:
```json
{
  "totalFiles": 12,
  "totalSizeBytes": 1234567890,
  "maxFiles": 100,
  "maxSizeBytes": 5368709120
}
```

`GET /cache/files`:
```json
[
  {
    "videoId": "abc123",
    "sizeBytes": 12345678,
    "lastModifiedEpochMs": 1709654321000
  }
]
```

## Testing

- **Unit tests** in `CacheServiceTest`: verify `getStats()` and `listFiles()` return correct values given a set of files on disk
- **Integration tests** in `IntegrationTest`: new `@Nested` classes `GetCacheStats` and `GetCacheFiles` covering:
  - Success case returns correct JSON shape
  - Auth enforcement (401 without credentials)
