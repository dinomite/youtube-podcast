# RSS Feed Artwork Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Populate `<itunes:image>` tags in both the channel block (show art) and item blocks (episode art) of the RSS feed by switching the yt-dlp playlist fetch to `--dump-single-json`.

**Architecture:** Switch `YtDlpExecutor` from `--dump-json` (NDJSON, one line per video) to `--dump-single-json` (one JSON object with playlist metadata + entries array). This gives us the playlist-level `thumbnail` field directly. Add a `Thumbnail` model and `bestThumbnail` computed property to `VideoMetadata` for picking the highest-resolution episode thumbnail from the `thumbnails` array yt-dlp includes per video.

**Tech Stack:** Kotlin, kotlinx.serialization, yt-dlp, JUnit 5, Kotest matchers

---

### Task 1: Add `Thumbnail` model and `VideoMetadata.bestThumbnail`

**Files:**
- Create: `src/main/kotlin/net/dinomite/ytpodcast/models/Thumbnail.kt`
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/models/VideoMetadata.kt`
- Test: `src/test/kotlin/net/dinomite/ytpodcast/models/VideoMetadataTest.kt`

**Step 1: Write failing tests**

Add to `VideoMetadataTest.kt` after the existing tests:

```kotlin
@Test
fun `bestThumbnail returns highest resolution thumbnail URL`() {
    val video = VideoMetadata(
        id = "test",
        title = "Test",
        thumbnails = listOf(
            Thumbnail(url = "https://example.com/small.jpg", width = 168, height = 94),
            Thumbnail(url = "https://example.com/large.jpg", width = 336, height = 188),
            Thumbnail(url = "https://example.com/medium.jpg", width = 246, height = 138),
        ),
    )

    video.bestThumbnail shouldBe "https://example.com/large.jpg"
}

@Test
fun `bestThumbnail falls back to thumbnail field when thumbnails is empty`() {
    val video = VideoMetadata(id = "test", title = "Test", thumbnail = "https://example.com/fallback.jpg")

    video.bestThumbnail shouldBe "https://example.com/fallback.jpg"
}

@Test
fun `bestThumbnail returns null when both thumbnails and thumbnail are absent`() {
    val video = VideoMetadata(id = "test", title = "Test")

    video.bestThumbnail shouldBe null
}
```

You also need to add the import for `Thumbnail` at the top of the file:
```kotlin
import net.dinomite.ytpodcast.models.Thumbnail
```

**Step 2: Run tests to confirm failure**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.models.VideoMetadataTest"
```

Expected: FAIL — `Thumbnail` and `bestThumbnail` do not exist yet.

**Step 3: Create `Thumbnail.kt`**

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int? = null,
    val width: Int? = null,
)
```

**Step 4: Add `thumbnails` and `bestThumbnail` to `VideoMetadata`**

Add `thumbnails` as a constructor parameter (after `thumbnail`):

```kotlin
val thumbnails: List<Thumbnail> = emptyList(),
```

Add `bestThumbnail` as a computed property in the class body (after the closing `)` of the constructor, add `{` and `}`):

```kotlin
) {
    val bestThumbnail: String?
        get() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: thumbnail
}
```

The full updated `VideoMetadata.kt` should look like:

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
    val duration: Int? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    val uploader: String? = null,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("playlist_title") val playlistTitle: String? = null,
    @SerialName("playlist_uploader") val playlistUploader: String? = null,
) {
    val bestThumbnail: String?
        get() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: thumbnail
}
```

**Step 5: Run tests to confirm pass**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.models.VideoMetadataTest"
```

Expected: PASS all tests including the 3 new ones.

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/models/Thumbnail.kt \
        src/main/kotlin/net/dinomite/ytpodcast/models/VideoMetadata.kt \
        src/test/kotlin/net/dinomite/ytpodcast/models/VideoMetadataTest.kt
git commit -m "feat: add Thumbnail model and VideoMetadata.bestThumbnail"
```

---

### Task 2: Switch playlist fetch to `--dump-single-json` and simplify parsing

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutorTest.kt`
- Modify: `src/test/resources/test-playlist.json`

**Step 1: Update command test**

In `YtDlpExecutorTest.kt`, update the `buildPlaylistCommand builds correct command` test:

```kotlin
@Test
fun `buildPlaylistCommand builds correct command`() {
    val command = YtDlpExecutor.buildPlaylistCommand("PLtest123")

    command shouldBe listOf(
        "yt-dlp",
        "--flat-playlist",
        "--dump-single-json",
        "https://www.youtube.com/playlist?list=PLtest123",
    )
}
```

**Step 2: Update `parsePlaylistJson` tests**

Replace the two existing `parsePlaylistJson` tests in `YtDlpExecutorTest.kt` with:

```kotlin
@Test
fun `parsePlaylistJson throws on invalid JSON`() {
    val exception = shouldThrow<YtDlpException> {
        YtDlpExecutor.parsePlaylistJson("not valid json", json)
    }
    exception.message shouldContain "parse"
}

@Test
fun `parsePlaylistJson parses valid single JSON`() {
    val singleJson = """
        {
            "id": "PLtest",
            "title": "Test Playlist",
            "uploader": "Test Channel",
            "thumbnail": "https://example.com/playlist-thumb.jpg",
            "entries": [
                {"id": "v1", "title": "Video 1"},
                {"id": "v2", "title": "Video 2"}
            ]
        }
    """.trimIndent()

    val playlist = YtDlpExecutor.parsePlaylistJson(singleJson, json)

    playlist.id shouldBe "PLtest"
    playlist.title shouldBe "Test Playlist"
    playlist.uploader shouldBe "Test Channel"
    playlist.thumbnail shouldBe "https://example.com/playlist-thumb.jpg"
    playlist.entries.size shouldBe 2
    playlist.entries[0].id shouldBe "v1"
    playlist.entries[1].id shouldBe "v2"
}

@Test
fun `parsePlaylistJson parses single JSON from real yt-dlp output`() {
    val singleJson = this::class.java.getResource("/test-playlist.json")!!.readText()

    val playlist = YtDlpExecutor.parsePlaylistJson(singleJson, json)

    playlist.id shouldBe "PLQlnTldJs0ZSINUQoJ2lY-z1HKO-XUWP8"
    playlist.title shouldBe "Greeking Out Podcast Season 12 | Nat Geo Kids"
    playlist.uploader shouldBe "Nat Geo Kids"
    playlist.thumbnail shouldBe "https://i.ytimg.com/vi/gUIStb1aVxg/hqdefault.jpg"
    playlist.entries.size shouldBe 11
    playlist.entries[0].id shouldBe "gUIStb1aVxg"
    playlist.entries[0].title shouldContain "Percy Jackson"
    playlist.entries[10].id shouldBe "P0agJv2JAVM"
    playlist.entries[10].title shouldContain "Snake Stories"
}
```

**Step 3: Convert `test-playlist.json` to single-JSON format**

Replace the contents of `src/test/resources/test-playlist.json` with a single JSON object. The structure is:

```json
{
  "id": "PLQlnTldJs0ZSINUQoJ2lY-z1HKO-XUWP8",
  "title": "Greeking Out Podcast Season 12 | Nat Geo Kids",
  "uploader": "Nat Geo Kids",
  "thumbnail": "https://i.ytimg.com/vi/gUIStb1aVxg/hqdefault.jpg",
  "entries": [
    <line 1 of existing NDJSON>,
    <line 2 of existing NDJSON>,
    ...all 11 lines...
  ]
}
```

Each line of the existing NDJSON file becomes one element in the `entries` array, separated by commas. The outer object uses the playlist ID and title from the existing entries' `playlist_id` and `playlist_title` fields (visible in each NDJSON line). The thumbnail URL `"https://i.ytimg.com/vi/gUIStb1aVxg/hqdefault.jpg"` is the base URL of the first video's smallest thumbnail (without query parameters — use a clean URL).

**Step 4: Run tests to confirm failures**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.util.YtDlpExecutorTest"
```

Expected: FAIL — command still emits `--dump-json`, `parsePlaylistJson` still does NDJSON parsing.

**Step 5: Update `buildPlaylistCommand` in `YtDlpExecutor.kt`**

Change `"--dump-json"` to `"--dump-single-json"`:

```kotlin
fun buildPlaylistCommand(playlistId: String): List<String> = listOf(
    "yt-dlp",
    "--flat-playlist",
    "--dump-single-json",
    "https://www.youtube.com/playlist?list=$playlistId",
)
```

**Step 6: Simplify `parsePlaylistJson` in `YtDlpExecutor.kt`**

Replace the entire body of `parsePlaylistJson` with a direct deserialization:

```kotlin
fun parsePlaylistJson(jsonText: String, json: Json): PlaylistMetadata = try {
    json.decodeFromString<PlaylistMetadata>(jsonText)
} catch (e: SerializationException) {
    throw YtDlpException("Failed to parse playlist JSON: ${e.message}", e)
} catch (e: IllegalArgumentException) {
    throw YtDlpException("Failed to parse playlist JSON: ${e.message}", e)
}
```

Also add the `PlaylistMetadata` import to `YtDlpExecutor.kt` if it is not already there (check the imports at the top of the file).

**Step 7: Run tests to confirm pass**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.util.YtDlpExecutorTest"
```

Expected: PASS all tests. Then run the full suite to check for regressions:

```bash
./gradlew test
```

Expected: PASS all tests.

**Step 8: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutor.kt \
        src/test/kotlin/net/dinomite/ytpodcast/util/YtDlpExecutorTest.kt \
        src/test/resources/test-playlist.json
git commit -m "feat: switch playlist fetch to --dump-single-json for playlist thumbnail"
```

---

### Task 3: Wire `bestThumbnail` into `RssFeedService` and add episode art test

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/services/RssFeedService.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/services/RssFeedServiceTest.kt`

**Step 1: Write a failing test**

Add to `RssFeedServiceTest.kt` (add `Thumbnail` import alongside existing model imports):

```kotlin
import net.dinomite.ytpodcast.models.Thumbnail
```

Add this test:

```kotlin
@Test
fun `uses best thumbnail from thumbnails array for episode art`() {
    val playlist = PlaylistMetadata(
        id = "PLtest",
        title = "Test Playlist",
        entries = listOf(
            VideoMetadata(
                id = "video1",
                title = "First Video",
                thumbnails = listOf(
                    Thumbnail(url = "https://example.com/small.jpg", width = 168, height = 94),
                    Thumbnail(url = "https://example.com/large.jpg", width = 336, height = 188),
                ),
            ),
        ),
    )
    every { urlBuilder.buildEpisodeUrl("video1", any(), any(), any()) } returns
        "https://test.com/episode/video1.mp3"

    val rss = service.generateFeed(playlist, "https", "test.com", 443)

    rss shouldContain """<itunes:image href="https://example.com/large.jpg"/>"""
}
```

**Step 2: Run test to confirm failure**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.services.RssFeedServiceTest.uses best thumbnail from thumbnails array for episode art"
```

Expected: FAIL — `RssFeedService` still uses `video.thumbnail` which is null here.

**Step 3: Update `RssFeedService.appendItem`**

In `RssFeedService.kt`, change line 65 from:

```kotlin
video.thumbnail?.let { appendLine("""      <itunes:image href="$it"/>""") }
```

to:

```kotlin
video.bestThumbnail?.let { appendLine("""      <itunes:image href="$it"/>""") }
```

**Step 4: Run tests to confirm pass**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.services.RssFeedServiceTest"
```

Expected: PASS all tests including the new one. The existing `generates valid RSS feed with iTunes namespace` test passes because `bestThumbnail` falls back to `thumbnail` when `thumbnails` is empty.

Then run the full suite:

```bash
./gradlew test
```

Expected: PASS all tests.

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/RssFeedService.kt \
        src/test/kotlin/net/dinomite/ytpodcast/services/RssFeedServiceTest.kt
git commit -m "feat: use bestThumbnail for episode art in RSS feed"
```
