# YouTube URL Query Parameter for /show Endpoint — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add `GET /show?url=<youtube-url>` so callers can paste a full YouTube playlist URL and receive the same podcast RSS feed as `GET /show/{playlistId}`.

**Architecture:** A new `extractPlaylistId(url: String): String?` utility function parses the `list` query param from any YouTube playlist URL format. A new `/show` route in `RouteHandlers` validates the `url` query param, extracts the playlist ID, and delegates to the existing `handleShowRequest()`.

**Tech Stack:** Kotlin, Ktor 3.4+, JUnit 5, kotest matchers, `java.net.URI` for URL parsing.

---

### Task 1: `extractPlaylistId` utility — tests first

**Files:**
- Create: `src/test/kotlin/net/dinomite/ytpodcast/util/YouTubeUrlParserTest.kt`
- Create: `src/main/kotlin/net/dinomite/ytpodcast/util/YouTubeUrlParser.kt`

**Step 1: Write the failing tests**

Create `src/test/kotlin/net/dinomite/ytpodcast/util/YouTubeUrlParserTest.kt`:

```kotlin
package net.dinomite.ytpodcast.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class YouTubeUrlParserTest {
    @Test
    fun `extracts playlist ID from canonical playlist URL`() {
        extractPlaylistId("https://www.youtube.com/playlist?list=PLxxxxxx") shouldBe "PLxxxxxx"
    }

    @Test
    fun `extracts playlist ID from watch URL with list param`() {
        extractPlaylistId("https://www.youtube.com/watch?v=abc123&list=PLxxxxxx") shouldBe "PLxxxxxx"
    }

    @Test
    fun `extracts playlist ID from short youtu be URL`() {
        extractPlaylistId("https://youtu.be/abc123?list=PLxxxxxx") shouldBe "PLxxxxxx"
    }

    @Test
    fun `returns null for URL with no list param`() {
        extractPlaylistId("https://www.youtube.com/watch?v=abc123") shouldBe null
    }

    @Test
    fun `returns null for malformed URL`() {
        extractPlaylistId("not a url at all") shouldBe null
    }

    @Test
    fun `returns null for empty string`() {
        extractPlaylistId("") shouldBe null
    }
}
```

**Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.util.YouTubeUrlParserTest"
```

Expected: compilation failure — `extractPlaylistId` does not exist yet.

**Step 3: Implement `extractPlaylistId`**

Create `src/main/kotlin/net/dinomite/ytpodcast/util/YouTubeUrlParser.kt`:

```kotlin
package net.dinomite.ytpodcast.util

import java.net.URI

/**
 * Extracts the YouTube playlist ID (the `list` query parameter) from a YouTube URL.
 *
 * Supports:
 * - https://www.youtube.com/playlist?list=PLxxxxxx
 * - https://www.youtube.com/watch?v=xxxxxx&list=PLxxxxxx
 * - https://youtu.be/xxxxxx?list=PLxxxxxx
 *
 * @return the playlist ID, or null if the URL is malformed or has no `list` param
 */
fun extractPlaylistId(url: String): String? = runCatching {
    URI(url).query
        ?.split("&")
        ?.firstOrNull { it.startsWith("list=") }
        ?.removePrefix("list=")
        ?.takeIf { it.isNotEmpty() }
}.getOrNull()
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.util.YouTubeUrlParserTest"
```

Expected: all 6 tests pass.

**Step 5: Check style**

```bash
./gradlew ktlintCheck
```

If there are failures, run `./gradlew ktlintFormat` then re-check.

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/YouTubeUrlParser.kt \
        src/test/kotlin/net/dinomite/ytpodcast/util/YouTubeUrlParserTest.kt
git commit -m "feat: add extractPlaylistId utility for YouTube URL parsing"
```

---

### Task 2: New `/show?url=` route — tests first

**Files:**
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt`
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`

**Step 1: Add integration tests for the new route**

In `IntegrationTest.kt`, add a new `@Nested` inner class after the existing `GetShow` class. The test setup (stub executor, playlist) mirrors the existing `GetShow` success test — reuse the same playlist ID `PLtest123` and the same `PlaylistMetadata`.

```kotlin
@Nested
inner class GetShowByUrl {
    private val playlistId = "PLtest123"
    private val playlist = PlaylistMetadata(
        id = playlistId,
        title = "Test Playlist",
        description = "A test playlist for integration testing",
        uploader = "Test Channel",
        thumbnail = "https://example.com/thumb.jpg",
        entries = listOf(
            VideoMetadata(
                id = "video1",
                title = "First Video",
                description = "First video description",
                duration = 180,
                uploadDate = "20240115",
                uploader = "Test Channel",
            ),
        ),
    )

    @Test
    fun `GET show with canonical playlist URL returns RSS feed`() = testApplication {
        val stubExecutor = StubYtDlpExecutor().apply { givenPlaylist(playlistId, playlist) }
        application { testModuleWithStub(stubExecutor) }

        client.get("/show?url=https://www.youtube.com/playlist?list=$playlistId") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.OK
            contentType()?.withoutParameters() shouldBe ContentType.Application.Rss
            bodyAsText() shouldContain "<title>Test Playlist</title>"
        }
    }

    @Test
    fun `GET show with watch URL containing list param returns RSS feed`() = testApplication {
        val stubExecutor = StubYtDlpExecutor().apply { givenPlaylist(playlistId, playlist) }
        application { testModuleWithStub(stubExecutor) }

        client.get("/show?url=https://www.youtube.com/watch?v=abc123%26list=$playlistId") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.OK
            contentType()?.withoutParameters() shouldBe ContentType.Application.Rss
        }
    }

    @Test
    fun `GET show with youtu be URL returns RSS feed`() = testApplication {
        val stubExecutor = StubYtDlpExecutor().apply { givenPlaylist(playlistId, playlist) }
        application { testModuleWithStub(stubExecutor) }

        client.get("/show?url=https://youtu.be/abc123?list=$playlistId") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.OK
            contentType()?.withoutParameters() shouldBe ContentType.Application.Rss
        }
    }

    @Test
    fun `GET show without url param returns 400`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/show") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.BadRequest
            bodyAsText() shouldContain "bad_request"
            bodyAsText() shouldContain "Missing url parameter"
        }
    }

    @Test
    fun `GET show with URL missing list param returns 400`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/show?url=https://www.youtube.com/watch?v=abc123") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.BadRequest
            bodyAsText() shouldContain "bad_request"
            bodyAsText() shouldContain "Invalid YouTube URL"
        }
    }

    @Test
    fun `GET show with malformed URL returns 400`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/show?url=not+a+url") {
            basicAuth("testuser", "testpass")
        }.apply {
            status shouldBe HttpStatusCode.BadRequest
            bodyAsText() shouldContain "bad_request"
            bodyAsText() shouldContain "Invalid YouTube URL"
        }
    }

    @Test
    fun `GET show by url without credentials returns 401`() = testApplication {
        application { testModuleWithStub(StubYtDlpExecutor()) }

        client.get("/show?url=https://www.youtube.com/playlist?list=$playlistId").apply {
            status shouldBe HttpStatusCode.Unauthorized
        }
    }
}
```

Note: the `&` in multi-param URLs must be percent-encoded as `%26` when embedded inside another query param value.

**Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest\$GetShowByUrl"
```

Expected: tests fail — route does not exist yet; `/show` (no path param) likely returns 404 or 405.

**Step 3: Add the new route to `Routing.kt`**

In `src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt`, add a new method to `RouteHandlers` and register it in `configureRouting`.

Add to `RouteHandlers`:

```kotlin
fun registerShowByUrlRoute(route: Route) {
    route.get("/show") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("bad_request", "Missing url parameter"),
            )
        val playlistId = extractPlaylistId(url)
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("bad_request", "Invalid YouTube URL: $url"),
            )
        handleShowRequest(call, playlistId)
    }
}
```

Add the import at the top of `Routing.kt`:

```kotlin
import net.dinomite.ytpodcast.util.extractPlaylistId
```

Register the route in the `authenticate("podcast-auth")` block inside `configureRouting`:

```kotlin
authenticate("podcast-auth") {
    handlers.registerShowRoute(this)
    handlers.registerShowByUrlRoute(this)   // <-- add this line
    handlers.registerEpisodeRoute(this)
    handlers.registerCacheStatsRoute(this)
    handlers.registerCacheFilesRoute(this)
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.IntegrationTest\$GetShowByUrl"
```

Expected: all 7 tests pass.

**Step 5: Run the full test suite**

```bash
./gradlew test
```

Expected: all tests pass, no regressions.

**Step 6: Check style**

```bash
./gradlew ktlintCheck
```

If there are failures, run `./gradlew ktlintFormat` then re-check.

**Step 7: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/plugins/Routing.kt \
        src/test/kotlin/net/dinomite/ytpodcast/IntegrationTest.kt
git commit -m "feat: add /show?url= endpoint accepting full YouTube playlist URLs"
```

---

### Task 3: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update the API Routes section**

In the `**API Routes**` section of `CLAUDE.md`, add the new route:

```
- `GET /show?url=<youtube-url>` - RSS feed using a full YouTube playlist URL (canonical, watch, or youtu.be format)
```

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document /show?url= endpoint in CLAUDE.md"
```
