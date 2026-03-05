# Playlist Thumbnail Alignment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Update `Thumbnail`, `PlaylistMetadata`, and `RssFeedService` so that podcast RSS feeds correctly emit image tags when yt-dlp returns thumbnails as a list rather than a flat string.

**Architecture:** Add `id`/`resolution` to `Thumbnail`; mirror `VideoMetadata`'s `thumbnails` + `bestThumbnail` pattern in `PlaylistMetadata`; switch `RssFeedService` to call `playlist.bestThumbnail` instead of `playlist.thumbnail`.

**Tech Stack:** Kotlin, kotlinx.serialization, JUnit 5, Kotest matchers

---

### Task 1: Add `id` and `resolution` fields to `Thumbnail`

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/models/Thumbnail.kt`

Playlist-level thumbnails from yt-dlp include `id` and `resolution` fields not currently in the model. The lenient serializer ignores them today, but the model should match the actual data.

**Step 1: Edit `Thumbnail.kt`**

Replace the existing data class with:

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int? = null,
    val width: Int? = null,
    val id: String? = null,
    val resolution: String? = null,
)
```

**Step 2: Run the full test suite to confirm nothing broke**

```bash
./gradlew test
```

Expected: all tests pass (the new fields are optional with defaults, so existing tests are unaffected).

**Step 3: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/models/Thumbnail.kt
git commit -m "feat: add id and resolution fields to Thumbnail model"
```

---

### Task 2: Add `thumbnails` list and `bestThumbnail` to `PlaylistMetadata`

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadata.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadataTest.kt`

**Step 1: Write the failing tests**

In `PlaylistMetadataTest.kt`, add these three tests after the existing ones:

```kotlin
@Test
fun `bestThumbnail returns highest resolution thumbnail URL`() {
    val playlist = PlaylistMetadata(
        id = "PLtest",
        title = "Test",
        thumbnails = listOf(
            Thumbnail(url = "https://example.com/small.jpg", width = 240, height = 240),
            Thumbnail(url = "https://example.com/large.jpg", width = 720, height = 720),
            Thumbnail(url = "https://example.com/medium.jpg", width = 480, height = 480),
        ),
    )

    playlist.bestThumbnail shouldBe "https://example.com/large.jpg"
}

@Test
fun `bestThumbnail falls back to thumbnail field when thumbnails is empty`() {
    val playlist = PlaylistMetadata(
        id = "PLtest",
        title = "Test",
        thumbnail = "https://example.com/fallback.jpg",
    )

    playlist.bestThumbnail shouldBe "https://example.com/fallback.jpg"
}

@Test
fun `bestThumbnail returns null when both thumbnails and thumbnail are absent`() {
    val playlist = PlaylistMetadata(id = "PLtest", title = "Test")

    playlist.bestThumbnail shouldBe null
}

@Test
fun `parses thumbnails array from yt-dlp playlist JSON`() {
    val ytDlpJson = """
        {
            "id": "PLtest",
            "title": "Test Playlist",
            "thumbnails": [
                {"url": "https://example.com/240.jpg", "height": 240, "width": 240, "id": "0", "resolution": "240x240"},
                {"url": "https://example.com/720.jpg", "height": 720, "width": 720, "id": "2", "resolution": "720x720"},
                {"url": "https://example.com/480.jpg", "height": 480, "width": 480, "id": "1", "resolution": "480x480"}
            ],
            "entries": []
        }
    """.trimIndent()

    val playlist = json.decodeFromString<PlaylistMetadata>(ytDlpJson)

    playlist.thumbnails.size shouldBe 3
    playlist.bestThumbnail shouldBe "https://example.com/720.jpg"
}
```

**Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.models.PlaylistMetadataTest"
```

Expected: compilation error — `thumbnails` and `bestThumbnail` don't exist yet.

**Step 3: Update `PlaylistMetadata.kt`**

```kotlin
package net.dinomite.ytpodcast.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistMetadata(
    val id: String,
    val title: String,
    val description: String? = null,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
    val entries: List<VideoMetadata> = emptyList(),
) {
    val bestThumbnail: String?
        get() = thumbnails.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url ?: thumbnail
}
```

**Step 4: Run the tests to confirm they pass**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.models.PlaylistMetadataTest"
```

Expected: all tests pass.

**Step 5: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadata.kt \
        src/test/kotlin/net/dinomite/ytpodcast/models/PlaylistMetadataTest.kt
git commit -m "feat: add thumbnails list and bestThumbnail to PlaylistMetadata"
```

---

### Task 3: Use `bestThumbnail` in `RssFeedService`

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/services/RssFeedService.kt`
- Modify: `src/test/kotlin/net/dinomite/ytpodcast/services/RssFeedServiceTest.kt`

**Step 1: Write the failing test**

In `RssFeedServiceTest.kt`, add after the existing tests:

```kotlin
@Test
fun `uses best thumbnail from playlist thumbnails array for show art`() {
    val playlist = PlaylistMetadata(
        id = "PLtest",
        title = "Test Playlist",
        thumbnails = listOf(
            Thumbnail(url = "https://example.com/small.jpg", width = 240, height = 240),
            Thumbnail(url = "https://example.com/large.jpg", width = 720, height = 720),
        ),
        entries = emptyList(),
    )

    val rss = service.generateFeed(playlist, "https", "test.com", 443)

    rss shouldContain """<itunes:image href="https://example.com/large.jpg"/>"""
    rss shouldContain "<url>https://example.com/large.jpg</url>"
}
```

**Step 2: Run the test to confirm it fails**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.services.RssFeedServiceTest.uses best thumbnail from playlist thumbnails array for show art"
```

Expected: FAIL — image tags are absent because `RssFeedService` uses `playlist.thumbnail` (null here).

**Step 3: Update `RssFeedService.kt`**

Change lines 43–50 from:

```kotlin
playlist.thumbnail?.let {
    appendLine("""    <itunes:image href="$it"/>""")
    appendLine("    <image>")
    appendLine("      <url>$it</url>")
    appendLine("      <title>${escapeXml(playlist.title)}</title>")
    appendLine("      <link>https://www.youtube.com/playlist?list=${playlist.id}</link>")
    appendLine("    </image>")
}
```

To:

```kotlin
playlist.bestThumbnail?.let {
    appendLine("""    <itunes:image href="$it"/>""")
    appendLine("    <image>")
    appendLine("      <url>$it</url>")
    appendLine("      <title>${escapeXml(playlist.title)}</title>")
    appendLine("      <link>https://www.youtube.com/playlist?list=${playlist.id}</link>")
    appendLine("    </image>")
}
```

**Step 4: Run all RSS feed service tests**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.services.RssFeedServiceTest"
```

Expected: all tests pass. The existing `generates valid RSS feed with iTunes namespace` test still passes because `bestThumbnail` falls back to the `thumbnail` field.

**Step 5: Run the full test suite**

```bash
./gradlew test
```

Expected: all tests pass.

**Step 6: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/services/RssFeedService.kt \
        src/test/kotlin/net/dinomite/ytpodcast/services/RssFeedServiceTest.kt
git commit -m "fix: use bestThumbnail in RssFeedService so playlist image tags are emitted from thumbnails array"
```
