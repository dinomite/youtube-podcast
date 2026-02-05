# Cache Management Design

## Overview

Add managed caching to the YouTube podcast converter to prevent disk space exhaustion while optimizing performance through intelligent file retention. Downloaded audio files become a cache with configurable size and count limits, using LRU (Least Recently Used) eviction.

## Goals

1. **Prevent disk space exhaustion** - Enforce maximum storage limits
2. **Control resource usage** - Configurable constraints on cache size and file count
3. **Performance optimization** - Keep frequently accessed episodes cached for faster serving
4. **Full observability** - Comprehensive logging of all cache operations

## High-Level Architecture

### Component Overview

The cache sits between the routing layer and AudioService:

```
Request → RouteHandlers → CacheService → AudioService → yt-dlp
```

**CacheService responsibilities:**
- Check if requested audio file exists in cache
- Return cached file if present (update access time)
- Delegate to AudioService if cache miss
- Run eviction before downloads (ensure space available)
- Run cleanup on startup (enforce limits from previous runs)
- Log all cache operations (hits, misses, evictions)

**AudioService stays unchanged** - it still just downloads to a given file path. CacheService decides where files go and manages their lifecycle.

### Directory Structure

The `tempDir` becomes the cache directory. Files stored as `{videoId}.mp3`. CacheService scans this directory on startup and manages it throughout the application lifecycle.

### Dependency Chain

```kotlin
Application.module() {
    val appConfig = loadAppConfig(environment.config)
    val ytDlpExecutor = YtDlpExecutor()
    val audioService = AudioService(ytDlpExecutor, appConfig.tempDir)
    val cacheService = CacheService(audioService, appConfig.cacheConfig)

    configureRouting(appConfig, cacheService, ...)
}
```

RouteHandlers calls `cacheService.getAudioFile(videoId)` instead of directly calling AudioService.

## CacheService API and Core Logic

### Public API

```kotlin
class CacheService(
    private val audioService: AudioService,
    private val config: CacheConfig,
) {
    // Main entry point - get audio file (from cache or download)
    fun getAudioFile(videoId: String): File

    // Called once on application startup
    fun initialize()
}
```

### Main Workflow (getAudioFile)

1. Check if `{videoId}.mp3` exists in cache directory
2. If exists (cache HIT):
   - Log cache HIT
   - Touch file (update modification time for LRU)
   - Return file
3. If not exists (cache MISS):
   - Log cache MISS
   - Run eviction check (ensure space for new download)
   - Call `audioService.downloadToTempFile(videoId)`
   - Log download complete
   - Return file

### Initialization Workflow

Called once in `Application.module()` before starting the server:

1. Scan cache directory
2. Calculate total size and file count
3. Log current cache state
4. Run eviction if over limits
5. Log post-cleanup state

**Rationale:** Ensures cache is within bounds before serving any requests. Provides visibility into cache state at startup.

## Cache Eviction Logic

### When Eviction Runs

- On startup (via `initialize()`)
- Before each download (in `getAudioFile()` when cache miss)

### Eviction Algorithm

```kotlin
private fun evictIfNeeded() {
    val files = listCacheFiles()  // List all .mp3 files
    val totalSize = files.sumOf { it.length() }
    val totalCount = files.size

    // Check if within limits
    if (isWithinLimits(totalSize, totalCount)) {
        return  // No eviction needed
    }

    // Sort by last modified time (oldest first = least recently used)
    val sortedFiles = files.sortedBy { it.lastModified() }

    // Delete files until within limits
    var currentSize = totalSize
    var currentCount = totalCount

    for (file in sortedFiles) {
        if (isWithinLimits(currentSize, currentCount)) {
            break  // Stop when limits satisfied
        }

        val fileSize = file.length()
        val deleted = file.delete()
        if (deleted) {
            currentSize -= fileSize
            currentCount -= 1
            logger.info("Evicted: ${file.name}, size=${fileSize}, reason=LRU")
        } else {
            logger.warn("Failed to delete: ${file.name}")
        }
    }
}
```

### Limit Checking

- "unlimited" (0 or special value) means no limit for that dimension
- Must satisfy BOTH limits (if both are set)
- Example: 5GB limit with unlimited count → only check size

### Concurrent Access Handling

Files being served to clients when eviction runs:
- Delete anyway - let OS handle it
- Unix systems keep file content accessible to open file handles even after deletion
- Simplest approach, no tracking needed

## Configuration

### CacheConfig Data Class

```kotlin
data class CacheConfig(
    val maxSize: Long,      // bytes (0 = unlimited)
    val maxCount: Int,      // file count (0 = unlimited)
    val directory: String,  // cache directory path
)
```

### Application Configuration

In `application.conf`:

```hocon
ytpodcast {
    baseUrl = ""
    baseUrl = ${?BASE_URL}

    tempDir = ""
    tempDir = ${?TEMP_DIR}

    cache {
        maxSize = "5GB"
        maxSize = ${?CACHE_MAX_SIZE}

        maxCount = 100
        maxCount = ${?CACHE_MAX_COUNT}
    }
}
```

### Loading Configuration

In `Application.kt`:

```kotlin
fun loadCacheConfig(config: ApplicationConfig, tempDir: String): CacheConfig {
    val sizeStr = config.propertyOrNull("ytpodcast.cache.maxSize")?.getString() ?: "5GB"
    val maxSize = parseSize(sizeStr)  // Convert "5GB" → bytes

    val maxCount = config.propertyOrNull("ytpodcast.cache.maxCount")?.getString()?.toInt() ?: 100

    return CacheConfig(maxSize, maxCount, tempDir)
}
```

### Size Parsing Utility

```kotlin
fun parseSize(size: String): Long {
    val pattern = """(\d+)\s*(B|KB|MB|GB|TB)?""".toRegex(RegexOption.IGNORE_CASE)
    val match = pattern.matchEntire(size.trim()) ?: throw IllegalArgumentException("Invalid size: $size")

    val amount = match.groupValues[1].toLong()
    val unit = match.groupValues[2].uppercase().ifEmpty { "B" }

    return when (unit) {
        "B" -> amount
        "KB" -> amount * 1024
        "MB" -> amount * 1024 * 1024
        "GB" -> amount * 1024 * 1024 * 1024
        "TB" -> amount * 1024 * 1024 * 1024 * 1024
        else -> throw IllegalArgumentException("Unknown unit: $unit")
    }
}
```

**Special values:** "0", "0B", "unlimited" all parse to `0` (unlimited).

**Defaults:** 5GB / 100 files - balances caching benefits with resource safety.

## Access Tracking (LRU Maintenance)

### Touching Files on Cache Hit

```kotlin
fun getAudioFile(videoId: String): File {
    val cacheFile = File(config.directory, "$videoId.mp3")

    if (cacheFile.exists()) {
        logger.info("Cache HIT: videoId=$videoId")
        touchFile(cacheFile)  // Update access time
        return cacheFile
    }

    // Cache miss logic...
}

private fun touchFile(file: File) {
    val now = System.currentTimeMillis()
    val updated = file.setLastModified(now)

    if (!updated) {
        logger.warn("Failed to update access time: ${file.name}")
    }
}
```

### Why Touch Files?

- Updates file modification timestamp to current time
- Makes recently accessed files "newer" in the LRU ordering
- When eviction runs, sorts by `file.lastModified()` - oldest files deleted first
- Survives application restarts (persisted in file system metadata)

### Newly Downloaded Files

- No need to touch them explicitly
- OS sets modification time to current time when file is created
- Automatically treated as "most recently used"

### Edge Case - Touch Failures

- Log warning but continue serving the file
- File remains at old timestamp (may be evicted sooner than ideal)
- Non-critical - cache still functions, just slightly less optimal LRU

## Logging and Observability

### Startup/Initialization

```kotlin
logger.info("Cache initialization starting: directory=${config.directory}")
logger.info("Cache limits: maxSize=${formatSize(config.maxSize)}, maxCount=${config.maxCount}")
logger.info("Cache current state: files=$count, size=${formatSize(totalSize)}")
// After eviction if needed:
logger.info("Cache post-cleanup: files=$count, size=${formatSize(totalSize)}")
```

### Cache Hits

```kotlin
logger.info("Cache HIT: videoId=$videoId, file=${file.name}, size=${formatSize(file.length())}")
```

### Cache Misses

```kotlin
logger.info("Cache MISS: videoId=$videoId")
logger.info("Downloading: videoId=$videoId")
logger.info("Download complete: videoId=$videoId, size=${formatSize(file.length())}")
```

### Evictions

```kotlin
logger.info("Eviction starting: currentFiles=$count, currentSize=${formatSize(size)}, reason=over-limit")
logger.info("Evicted: file=$fileName, size=${formatSize(fileSize)}, age=${ageInMinutes}min")
logger.info("Eviction complete: removedFiles=$removed, freedSpace=${formatSize(freed)}")
```

### Warnings/Errors

```kotlin
logger.warn("Failed to delete file: $fileName")
logger.warn("Failed to update access time: $fileName")
logger.error("Cache directory not accessible: ${config.directory}", exception)
```

### Helper for Human-Readable Sizes

```kotlin
private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024*1024*1024 -> "%.2f GB".format(bytes / (1024.0*1024*1024))
        bytes >= 1024*1024 -> "%.2f MB".format(bytes / (1024.0*1024))
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
```

## Error Handling

### Cache Directory Issues

```kotlin
fun initialize() {
    val cacheDir = File(config.directory)

    if (!cacheDir.exists()) {
        logger.warn("Cache directory doesn't exist, creating: ${config.directory}")
        val created = cacheDir.mkdirs()
        if (!created) {
            logger.error("Failed to create cache directory: ${config.directory}")
            throw IllegalStateException("Cannot initialize cache")
        }
    }

    if (!cacheDir.canRead() || !cacheDir.canWrite()) {
        logger.error("Cache directory not accessible: ${config.directory}")
        throw IllegalStateException("Cannot access cache directory")
    }

    // Continue with cache scan...
}
```

### Eviction Failures

- If individual file deletion fails, log warning and continue to next file
- Don't throw exception - partial cleanup is better than none
- May end up slightly over limit, but cache remains functional

### Download Failures

- If AudioService throws exception, let it propagate to RouteHandlers
- Existing error handling in Routing.kt handles it (returns 500 or 404)
- Cache remains consistent - no partial files added

### Disk Full During Download

- yt-dlp will fail with exception
- Exception propagates to client (500 error)
- Eviction before download should prevent this, but can't guarantee (concurrent requests)

### Configuration Errors

- Invalid size format (e.g., "5XB") → throw exception at startup
- Negative limits → treat as 0 (unlimited)
- Missing config values → use defaults (5GB/100 files)

### Error Handling Principle

**Fail fast at startup** (configuration/directory issues), but **be resilient during runtime** (log and continue on non-critical failures).

## Testing Strategy

### Unit Tests for CacheService

```kotlin
class CacheServiceTest {
    private val mockAudioService = mockk<AudioService>()
    private lateinit var tempDir: File
    private lateinit var cacheService: CacheService

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory().toFile()
        val config = CacheConfig(
            maxSize = 1024 * 1024,  // 1MB
            maxCount = 3,
            directory = tempDir.absolutePath
        )
        cacheService = CacheService(mockAudioService, config)
    }

    @Test
    fun `cache hit returns existing file and updates access time`()

    @Test
    fun `cache miss downloads file via AudioService`()

    @Test
    fun `eviction removes oldest files when count limit exceeded`()

    @Test
    fun `eviction removes oldest files when size limit exceeded`()

    @Test
    fun `eviction respects both size and count limits`()

    @Test
    fun `unlimited size (0) disables size limit`()

    @Test
    fun `unlimited count (0) disables count limit`()

    @Test
    fun `initialize scans directory and evicts if needed`()

    @Test
    fun `failed file deletion logs warning but continues`()
}
```

### Unit Tests for Configuration Parsing

```kotlin
class CacheConfigTest {
    @Test
    fun `parseSize handles GB format`()

    @Test
    fun `parseSize handles MB format`()

    @Test
    fun `parseSize handles case insensitive units`()

    @Test
    fun `parseSize treats 0 as unlimited`()

    @Test
    fun `parseSize throws on invalid format`()

    @Test
    fun `loadCacheConfig uses defaults when not specified`()

    @Test
    fun `loadCacheConfig loads from application config`()
}
```

### Integration Tests

Update existing `IntegrationTest.kt` to verify cache behavior:
- Multiple requests for same episode return cached file (check response time or logs)
- Cache eviction happens when limits exceeded
- Files persist across requests (cache hit)

### Test Utilities

Helper to create fake audio files with specific sizes and timestamps for eviction testing.

## Migration Path and Implementation

### Files to Create

- `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt` - Main cache logic
- `src/main/kotlin/net/dinomite/ytpodcast/config/CacheConfig.kt` - Configuration data class
- `src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt` - Unit tests
- `src/test/kotlin/net/dinomite/ytpodcast/config/CacheConfigTest.kt` - Config parsing tests

### Files to Modify

- `src/main/kotlin/net/dinomite/ytpodcast/Application.kt` - Add `loadCacheConfig()`, instantiate CacheService, call `initialize()`
- `src/main/resources/application.conf` - Add cache configuration section
- `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt` - Accept CacheService instead of AudioService in RouteHandlers
- `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt` - Update to use CacheService in test setup
- `README.md` - Document new cache configuration options
- `CLAUDE.md` - Update architecture documentation

### Implementation Sequence

1. **Configuration foundation** - Create CacheConfig, implement size parsing, add tests
2. **Basic CacheService** - Implement without eviction (pass-through to AudioService), add to wiring
3. **Eviction logic** - Add LRU eviction, test thoroughly
4. **Access tracking** - Add file touching on cache hits
5. **Initialization** - Add startup scan and cleanup
6. **Logging** - Add comprehensive logging throughout
7. **Integration** - Wire through Routing, update tests
8. **Documentation** - Update README and CLAUDE.md

### Backward Compatibility

- Existing audio files in tempDir automatically become cache
- Default limits (5GB/100) are generous - unlikely to immediately evict existing files
- No breaking changes to API endpoints

## Summary

This design adds a managed cache layer that:

1. **Controls resource usage** through configurable size and count limits
2. **Optimizes performance** by retaining frequently accessed files
3. **Uses LRU eviction** to intelligently manage limited storage
4. **Provides full observability** with comprehensive logging
5. **Maintains simplicity** through clean separation of concerns (CacheService wrapper)
6. **Ensures reliability** through fail-fast startup and resilient runtime behavior

The implementation follows SOLID principles, is thoroughly testable, and integrates cleanly with the existing architecture.