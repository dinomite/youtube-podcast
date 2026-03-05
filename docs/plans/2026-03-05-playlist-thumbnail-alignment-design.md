# Playlist Thumbnail Alignment Design

## Problem

The `PlaylistMetadata` model only has a `thumbnail: String?` field, but the current yt-dlp output
returns a `thumbnails` array at the playlist level (no flat `thumbnail` string). As a result,
podcast RSS feeds generated from fresh yt-dlp output have no image tags.

`VideoMetadata` already handles this correctly via a `thumbnails: List<Thumbnail>` field and a
`bestThumbnail` computed property. `PlaylistMetadata` needs the same treatment.

Additionally, the `Thumbnail` model is missing `id` and `resolution` fields that yt-dlp includes
in playlist thumbnail objects. While the lenient serializer silently ignores them today, the model
should accurately represent the actual data.

## Changes

### `Thumbnail.kt`

Add optional `id: String?` and `resolution: String?` fields so the model fully represents what
yt-dlp returns for both playlist and video thumbnails.

### `PlaylistMetadata.kt`

- Add `thumbnails: List<Thumbnail> = emptyList()`
- Add `bestThumbnail` computed property (identical logic to `VideoMetadata`):
  picks the thumbnail with the largest area, falling back to the flat `thumbnail` string

### `RssFeedService.kt`

Replace `playlist.thumbnail` references with `playlist.bestThumbnail`.

### Tests

- `PlaylistMetadataTest`: assert `bestThumbnail` returns the largest thumbnail from the list,
  and falls back to `thumbnail` when the list is empty
- RSS feed tests: assert image tags are emitted when thumbnails are provided via the list
