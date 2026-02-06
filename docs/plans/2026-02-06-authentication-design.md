# Authentication Design

## Overview

Add HTTP Basic Authentication to the YouTube to Podcast RSS service to protect access to RSS feeds and audio files. This design targets mainstream podcast clients (Apple Podcasts, Overcast, Pocket Casts) which natively support Basic Auth in feed URLs.

## Requirements

- Protect `/show/{playlistId}` (RSS feed) and `/episode/{videoId}.mp3` (audio files)
- Keep `/` and `/health` endpoints public for monitoring
- Single username/password credential pair
- Configuration via `application.conf` with environment variable overrides
- Application fails to start if credentials are not configured

## Configuration

### AppConfig Extension

Add authentication fields to `AppConfig` data class:

```kotlin
data class AppConfig(
    val baseUrl: String?,
    val tempDir: Path,
    val cacheDir: Path,
    val authUsername: String,
    val authPassword: String
)
```

### application.conf

```hocon
ytpodcast {
    auth {
        username = "dev"
        username = ${?AUTH_USERNAME}
        password = "dev"
        password = ${?AUTH_PASSWORD}
    }
}
```

The `${?VAR}` syntax allows environment variables to override config file values.

### Validation

In `AppConfig.load()`, validate that both `authUsername` and `authPassword` are non-empty. Throw a configuration exception if either is missing, preventing startup without credentials.

## Implementation

### Authentication Plugin

Create `plugins/Authentication.kt`:

```kotlin
fun Application.configureAuthentication(appConfig: AppConfig) {
    install(Authentication) {
        basic("podcast-auth") {
            realm = "YouTube Podcast RSS"
            validate { credentials ->
                if (credentials.name == appConfig.authUsername &&
                    credentials.password == appConfig.authPassword) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}
```

### Application Integration

In `Application.module()`, call `configureAuthentication(appConfig)` before `configureRouting()`:

```kotlin
fun Application.module() {
    val appConfig = AppConfig.load(environment.config)
    val cacheConfig = CacheConfig(environment.config, appConfig.cacheDir)

    // ... service initialization ...

    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureAuthentication(appConfig)  // Add this
    configureRouting(appConfig, youTubeMetadataService, cacheService)
}
```

### Route Protection

In `plugins/Routing.kt`, wrap protected routes in `authenticate()` block:

```kotlin
routing {
    get("/") {
        call.respondText("YouTube to Podcast RSS Feed Converter")
    }

    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
    }

    authenticate("podcast-auth") {
        handlers.registerShowRoute(this)
        handlers.registerEpisodeRoute(this)
    }
}
```

## Error Handling

When authentication fails:
- **Status**: `401 Unauthorized`
- **Header**: `WWW-Authenticate: Basic realm="YouTube Podcast RSS"`
- **Behavior**: Podcast clients automatically prompt users for credentials

Ktor's authentication plugin handles this automatically - no custom error handling needed.

## Testing

### Test Structure

Add to `IntegrationTest.kt`:

```kotlin
@Nested
inner class Authentication {
    @Test
    fun `GET show with valid credentials returns RSS feed`()

    @Test
    fun `GET show without credentials returns 401`()

    @Test
    fun `GET show with invalid credentials returns 401`()

    @Test
    fun `GET episode with valid credentials returns audio`()

    @Test
    fun `GET episode without credentials returns 401`()

    @Test
    fun `GET health without credentials succeeds`()
}
```

### Test Infrastructure

Use Ktor's test client to set Basic Auth headers:
- `basicAuthHeader(username, password)` helper
- Or manual `Authorization: Basic <base64>` header construction

## Dependencies

Add to `gradle/libs.versions.toml`:
- `io.ktor:ktor-server-auth`
- `io.ktor:ktor-server-auth-jvm`

## Breaking Changes

This is a **breaking change** - existing RSS feed URLs will require authentication.

**Before**: `https://yourserver.com/show/playlistId`
**After**: `https://user:pass@yourserver.com/show/playlistId`

Users must update their podcast app subscriptions with credentials embedded in the URL.

## Deployment Notes

### Development
Set simple credentials in `application.conf`:
```hocon
auth {
    username = "dev"
    password = "dev"
}
```

### Production
Override with environment variables:
```bash
export AUTH_USERNAME="your_username"
export AUTH_PASSWORD="your_secure_password"
```

Application will fail to start if these are not set, preventing accidental unprotected deployment.
