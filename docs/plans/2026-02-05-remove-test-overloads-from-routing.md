# Remove Test-Only Overloads from Routing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove test-only `configureRouting()` overloads from production code by introducing a test helper function.

**Architecture:** Create a `testModule()` extension function in test code that handles test-specific service wiring, allowing `configureRouting()` to accept only production dependencies (AppConfig + CacheService). Tests will build their own CacheService with StubYtDlpExecutor when needed.

**Tech Stack:** Kotlin, Ktor 3.4+, JUnit 5, Kotest matchers

---

## Task 1: Create test helper function for ApplicationTest

**Files:**
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/ApplicationTest.kt`

**Step 1: Add test module helper function to ApplicationTest**

Replace the existing `testModule()` function with one that constructs the full service stack:

```kotlin
private fun Application.testModule() {
    val appConfig = AppConfig()
    val cacheConfig = CacheConfig(
        maxSize = 0L,
        maxCount = 0,
        directory = appConfig.tempDir
    )
    val ytDlpExecutor = YtDlpExecutor()
    val audioService = AudioService(ytDlpExecutor, cacheConfig.directory)
    val cacheService = CacheService(audioService, cacheConfig)

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(appConfig, cacheService)
}
```

Add imports:

```kotlin
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.CacheService
import net.dinomite.ytpodcast.util.YtDlpExecutor
```

**Step 2: Run ApplicationTest to verify it still passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.ApplicationTest"`

Expected: All 5 tests PASS

**Step 3: Commit**

```bash
git add src/test/kotlin/net/dinomite/ytpodcast/ApplicationTest.kt
git commit -m "test: add service wiring to ApplicationTest testModule"
```

---

## Task 2: Update IntegrationTest to wire services directly

**Files:**
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt`

**Step 1: Update testModuleWithStub to build CacheService**

Replace the existing `testModuleWithStub()` function:

```kotlin
private fun Application.testModuleWithStub(stubExecutor: StubYtDlpExecutor) {
    val appConfig = AppConfig(
        baseUrl = "https://test.example.com",
        tempDir = System.getProperty("java.io.tmpdir")
    )
    val cacheConfig = CacheConfig(
        maxSize = 0L,
        maxCount = 0,
        directory = appConfig.tempDir
    )
    val audioService = AudioService(stubExecutor, cacheConfig.directory)
    val cacheService = CacheService(audioService, cacheConfig)

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureRouting(appConfig, cacheService)
}
```

Add imports:

```kotlin
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.services.AudioService
import net.dinomite.ytpodcast.services.CacheService
```

**Step 2: Run IntegrationTest to verify it still passes**

Run: `./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest"`

Expected: All 4 tests PASS

**Step 3: Commit**

```bash
git add src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt
git commit -m "test: wire CacheService in IntegrationTest with stub executor"
```

---

## Task 3: Remove test-only overloads from Routing.kt

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`

**Step 1: Remove the two test overload functions**

Delete lines 53-85 (the two test overload functions):

```kotlin
// Test overload that accepts only AppConfig (for ApplicationTest)
fun Application.configureRouting(appConfig: AppConfig) {
    configureRouting(appConfig, YtDlpExecutor())
}

// Test overload that accepts a YtDlpExecutor for dependency injection
fun Application.configureRouting(
    appConfig: AppConfig,
    ytDlpExecutor: YtDlpExecutor,
) {
    val youTubeMetadataService = YouTubeMetadataService(ytDlpExecutor)
    val urlBuilder = UrlBuilder(appConfig.baseUrl)
    val rssFeedService = RssFeedService(urlBuilder)
    val cacheService = CacheService(
        AudioService(ytDlpExecutor, appConfig.tempDir),
        CacheConfig(maxSize = 0, maxCount = 0, directory = appConfig.tempDir),
    )

    val handlers = RouteHandlers(youTubeMetadataService, rssFeedService, cacheService)

    routing {
        get("/") {
            call.respondText("YouTube to Podcast RSS Feed Converter")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        handlers.registerShowRoute(this)
        handlers.registerEpisodeRoute(this)
    }
}
```

**Step 2: Remove unused imports**

Remove these imports that are no longer needed in Routing.kt:

```kotlin
import net.dinomite.ytpodcast.config.CacheConfig
import net.dinomite.ytpodcast.services.AudioService
```

Keep `YtDlpExecutor` import - it's still used in the production overload.

**Step 3: Run all tests to verify everything still works**

Run: `./gradlew test`

Expected: All 39 tests PASS

**Step 4: Run ktlint and detekt to ensure code quality**

Run: `./gradlew ktlintCheck detekt`

Expected: No issues

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt
git commit -m "refactor: remove test-only overloads from Routing.kt"
```

---

## Task 4: Verify the full build

**Files:**
- N/A (verification only)

**Step 1: Run full build with all checks**

Run: `./gradlew check`

Expected: BUILD SUCCESSFUL with all tests passing, ktlint clean, detekt clean

**Step 2: Verify no test-only code remains in src/main**

Run: `grep -r "test" src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`

Expected: No results (or only results from comments/strings, not function names)

**Step 3: Review the changes**

Run: `git log --oneline -3`

Expected: See three commits:
1. refactor: remove test-only overloads from Routing.kt
2. test: wire CacheService in IntegrationTest with stub executor
3. test: add service wiring to ApplicationTest testModule

---

## Summary

This plan removes test-only code from production by:

1. **ApplicationTest**: Building full service stack directly in test helper
2. **IntegrationTest**: Wiring CacheService with StubYtDlpExecutor in test helper
3. **Routing.kt**: Removing both test overloads (single-arg and YtDlpExecutor-arg)
4. **Verification**: Running full test suite and build checks

The implementation maintains test coverage while keeping production code clean. All service construction logic moves to test code where it belongs.
