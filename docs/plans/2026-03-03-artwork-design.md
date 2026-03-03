# Artwork in RSS Feed

## Overview

Add show-level and episode-level artwork to the RSS feed produced by `/show/{playlistId}`.
The feed already emits `<itunes:image>` tags in the right places; they are just never populated
because the current yt-dlp invocation does not surface thumbnail URLs into the model.

## Approach

Switch the playlist fetch from `--dump-json` (NDJSON, one line per video) to
`--dump-single-json` (one JSON object with a `thumbnail` field and an `entries` array).
This provides the playlist-level thumbnail directly from yt-dlp with no additional call.
The per-video `thumbnails` array (already present in flat-playlist output) is used for
episode-level art by selecting the highest-resolution entry.

## Data Model Changes

**New `Thumbnail` data class** (`models/Thumbnail.kt`):
```kotlin
@Serializable
data class Thumbnail(val url: String, val height: Int? = null, val width: Int? = null)
```

**`VideoMetadata`** additions:
- `thumbnails: List<Thumbnail> = emptyList()` — the array yt-dlp includes per video
- `bestThumbnail: String?` computed property — picks the highest-resolution thumbnail
  (`max by width * height`), falling back to the singular `thumbnail` field

**`PlaylistMetadata`** — no changes needed. Its existing `thumbnail: String?` field
is populated automatically when deserializing yt-dlp's single-JSON output.

## yt-dlp / Parsing Changes

- `buildPlaylistCommand()`: replace `--dump-json` with `--dump-single-json`
- `parsePlaylistJson()`: replace NDJSON line-by-line parsing with a single
  `json.decodeFromString<PlaylistMetadata>(jsonText)` call (field names already match)

## RSS Feed Changes

- Channel-level `<itunes:image>`: already emitted from `playlist.thumbnail` — now populated
- Episode-level `<itunes:image>`: change `video.thumbnail` to `video.bestThumbnail`

No structural changes to the XML output.

## Out of Scope

- Embedding artwork in MP3 ID3 tags — not worth the complexity

## Testing

- **`test-playlist.json`**: replace NDJSON fixture with a single-JSON object (same video
  data wrapped in an outer playlist object with `id`, `title`, `uploader`, `thumbnail`,
  and `entries`)
- **`YtDlpExecutorTest`**: update command assertion (`--dump-single-json`), update
  `parsePlaylistJson` tests, assert `playlist.thumbnail` is populated
- **`VideoMetadataTest`**: test `bestThumbnail` picks highest resolution; fallback to
  `thumbnail` when `thumbnails` is empty
- **`RssFeedServiceTest`**: assert `<itunes:image>` appears in channel block and per item
