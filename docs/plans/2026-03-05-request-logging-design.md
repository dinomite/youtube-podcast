# Request IP and User-Agent Logging Design

## Overview

Log the requestor's IP address and User-Agent for every request. The app sits behind a
reverse proxy that sets `X-Forwarded-For`, so Ktor's `XForwardedHeaders` plugin is used
to surface the real client IP via `call.request.origin.remoteHost`.

The existing `CallLogging` setup already captures User-Agent but currently logs nothing
because its filter (`startsWith("/api")`) matches none of the actual routes. This change
fixes that filter and adds the IP field.

## What Changes

**`gradle/libs.versions.toml`**
Add `ktor-server-forwarded-header` to the ktor libraries section:
```toml
ktor-server-forwardedHeader = { module = "io.ktor:ktor-server-forwarded-header", version.ref = "ktor" }
```
Add it to `build.gradle.kts` implementation dependencies.

**`src/main/kotlin/net/dinomite/ytpodcast/plugins/HTTP.kt`**
Install `XForwardedHeaders` plugin:
```kotlin
install(XForwardedHeaders)
```
This teaches Ktor to trust `X-Forwarded-For` so that `call.request.origin.remoteHost`
returns the real client IP throughout the application.

**`src/main/kotlin/net/dinomite/ytpodcast/plugins/Monitoring.kt`**
Two changes:
1. Remove the broken `filter` block (was `startsWith("/api")`, matched nothing)
2. Add `call.request.origin.remoteHost` to the log format

Resulting log line format:
```
GET /show/PLtest123 - 200 OK - 203.0.113.42 - User-Agent: AntennaPod/3.2.0
```

## Testing

- **`MonitoringTest`** — verify the log format includes IP and User-Agent for a request
- **Integration test** — send a request with an `X-Forwarded-For` header via Ktor's
  `testApplication` and assert `call.request.origin.remoteHost` returns the forwarded IP

## Out of Scope

- MDC-based per-line IP/UA injection
- Logging body or response size
