# Design: YouTube URL Query Parameter for /show Endpoint

## Overview

Add a `GET /show?url=<youtube-url>` route that accepts a full YouTube playlist URL copied from the browser and returns the same podcast RSS feed as the existing `GET /show/{playlistId}` endpoint.

## Routes

Two routes, one shared handler:

```
GET /show/{playlistId}    → extract playlistId from path → handleShowRequest()
GET /show?url=<yturl>     → parse url, extract list param → handleShowRequest()
```

The existing `/show/{playlistId}` route is unchanged.

## URL Parsing

A new `extractPlaylistId(url: String): String?` function in `util/` uses `java.net.URI` to parse the URL and extract the `list` query parameter.

Supported formats:
- `https://www.youtube.com/playlist?list=PLxxxxxx`
- `https://www.youtube.com/watch?v=xxxxxx&list=PLxxxxxx`
- `https://youtu.be/xxxxxx?list=PLxxxxxx`

Returns `null` if the URL is malformed or has no `list` param.

## Error Handling

The `/show?url=` route returns:

- `400 Bad Request` `{"code": "bad_request", "message": "Missing url parameter"}` — `url` query param absent
- `400 Bad Request` `{"code": "bad_request", "message": "Invalid YouTube URL: <url>"}` — URL unparseable or missing `list` param
- All downstream errors identical to existing `/show/{playlistId}` behavior (404 for not found, 500 for fetch errors)

## Testing

- Unit tests for `extractPlaylistId()` covering all three URL formats, malformed URLs, and URLs missing `list`
- Integration tests in `IntegrationTest.GetShow` covering:
  - Success with each URL format
  - Missing `url` param → 400
  - Invalid URL → 400
  - URL with no `list` param → 400
