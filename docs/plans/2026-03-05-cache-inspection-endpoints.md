# Cache Inspection Endpoints Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add authenticated `GET /cache/stats` and `GET /cache/files` endpoints that expose cache state (file count, total size, limits, and per-file metadata).

**Architecture:** Add two `@Serializable` model classes, expose two new public methods on `CacheService`, and register two new routes inside the existing `authenticate("podcast-auth")` block in `Routing.kt`.

**Tech Stack:** Kotlin, Ktor 3.4+, kotlinx.serialization, JUnit 5, kotest matchers

---

### Task 1: Add data model classes

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/models/CacheStats.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/models/CacheFileInfo.kt`

**Step 1: Create `CacheStats.kt`**

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class CacheStats(
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val maxFiles: Int,
    val maxSizeBytes: Long,
)
```

**Step 2: Create `CacheFileInfo.kt`**

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class CacheFileInfo(
    val videoId: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
)
```

**Step 3: Build to verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/models/CacheStats.kt \
        src/main/kotlin/net/dinomite/ytpodcast/models/CacheFileInfo.kt
git commit -m "feat: add CacheStats and CacheFileInfo models"
```

---

### Task 2: Add `getStats()` and `listFiles()` to `CacheService`

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt`

**Step 1: Write failing tests**

Add these tests to `CacheServiceTest` (inside the class, after existing tests):

```kotlin
@Test
fun `getStats returns correct totals and limits`() {
    val config = CacheConfig(
        maxSize = 5_000_000_000L,
        maxCount = 100,
        directory = tempDir.absolutePath
    )
    cacheService = CacheService(config)

    File(tempDir, "video1.mp3").writeBytes(ByteArray(1000))
    File(tempDir, "video2.mp3").writeBytes(ByteArray(2000))

    val stats = cacheService.getStats()

    stats.totalFiles shouldBe 2
    stats.totalSizeBytes shouldBe 3000L
    stats.maxFiles shouldBe 100
    stats.maxSizeBytes shouldBe 5_000_000_000L
}

@Test
fun `getStats returns zeros when cache is empty`() {
    val stats = cacheService.getStats()

    stats.totalFiles shouldBe 0
    stats.totalSizeBytes shouldBe 0L
    stats.maxFiles shouldBe 0    // unlimited (default config in setup)
    stats.maxSizeBytes shouldBe 0L
}

@Test
fun `listFiles returns file info for each cached file`() {
    val file1 = File(tempDir, "abc123.mp3").apply { writeBytes(ByteArray(500)) }
    val file2 = File(tempDir, "xyz789.mp3").apply { writeBytes(ByteArray(1500)) }

    val files = cacheService.listFiles().sortedBy { it.videoId }

    files.size shouldBe 2
    files[0].videoId shouldBe "abc123"
    files[0].sizeBytes shouldBe 500L
    files[1].videoId shouldBe "xyz789"
    files[1].sizeBytes shouldBe 1500L
}

@Test
fun `listFiles returns empty list when cache is empty`() {
    cacheService.listFiles() shouldBe emptyList()
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`
Expected: FAIL — `getStats` and `listFiles` are unresolved references

**Step 3: Implement the methods in `CacheService.kt`**

Add the following imports at the top of `CacheService.kt`:
```kotlin
import net.dinomite.ytpodcast.models.CacheFileInfo
import net.dinomite.ytpodcast.models.CacheStats
```

Change `listCacheFiles()` from `private` to `private` (keep private) and add these two public methods after `evictIfNeeded()`:

```kotlin
fun getStats(): CacheStats {
    val files = listCacheFiles()
    return CacheStats(
        totalFiles = files.size,
        totalSizeBytes = files.sumOf { it.length() },
        maxFiles = config.maxCount,
        maxSizeBytes = config.maxSize,
    )
}

fun listFiles(): List<CacheFileInfo> {
    return listCacheFiles().map { file ->
        CacheFileInfo(
            videoId = file.nameWithoutExtension,
            sizeBytes = file.length(),
            lastModifiedEpochMs = file.lastModified(),
        )
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.services.CacheServiceTest"`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/CacheService.kt \
        src/test/kotlin/net/dinomite/ytpodcast/services/CacheServiceTest.kt
git commit -m "feat: add getStats and listFiles methods to CacheService"
```

---

### Task 3: Add `/cache/stats` and `/cache/files` routes

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt`

**Step 1: Write failing integration tests**

Add two new `@Nested` classes to `IntegrationTest.kt` (after the `Authentication` class, inside `IntegrationTest`):

```kotlin
@Nested
inner class GetCacheStats {
    @Test
    fun `GET cache stats returns stats JSON with auth`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/cache/stats") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.OK
            val body = bodyAsText()
            body shouldContain "\"totalFiles\""
            body shouldContain "\"totalSizeBytes\""
            body shouldContain "\"maxFiles\""
            body shouldContain "\"maxSizeBytes\""
        }
    }

    @Test
    fun `GET cache stats without credentials returns 401`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/cache/stats").apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }
}

@Nested
inner class GetCacheFiles {
    @Test
    fun `GET cache files returns empty array for empty cache`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/cache/files") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "[]"
        }
    }

    @Test
    fun `GET cache files without credentials returns 401`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/cache/files").apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest"`
Expected: FAIL — 404 for `/cache/stats` and `/cache/files`

**Step 3: Add routes to `Routing.kt`**

In the `RouteHandlers` class, add two new registration methods after `registerEpisodeRoute()`:

```kotlin
fun registerCacheStatsRoute(route: Route) {
    route.get("/cache/stats") {
        call.respond(cacheService.getStats())
    }
}

fun registerCacheFilesRoute(route: Route) {
    route.get("/cache/files") {
        call.respond(cacheService.listFiles())
    }
}
```

In the `configureRouting()` function, add the two new route registrations inside the `authenticate("podcast-auth")` block, after `handlers.registerEpisodeRoute(this)`:

```kotlin
authenticate("podcast-auth") {
    handlers.registerShowRoute(this)
    handlers.registerEpisodeRoute(this)
    handlers.registerCacheStatsRoute(this)
    handlers.registerCacheFilesRoute(this)
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest"`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 5: Run full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

**Step 6: Run linter**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL. If there are style violations, run `./gradlew ktlintFormat` then re-check.

**Step 7: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt \
        src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt
git commit -m "feat: add /cache/stats and /cache/files endpoints"
```
