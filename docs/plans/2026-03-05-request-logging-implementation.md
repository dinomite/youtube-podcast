# Request IP and User-Agent Logging Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Log the real client IP (from `X-Forwarded-For`) and User-Agent for every request by installing Ktor's `XForwardedHeaders` plugin and fixing the broken `CallLogging` filter.

**Architecture:** Add `ktor-server-forwarded-header` as a dependency, install `XForwardedHeaders` in `configureHTTP()` so `call.request.origin.remoteHost` returns the real client IP throughout the app, then update `CallLogging` in `configureMonitoring()` to remove the broken `/api` filter and include the IP in the format string.

**Tech Stack:** Kotlin, Ktor 3.4+, SLF4J/Logback, Gradle version catalog

---

### Task 1: Add `ktor-server-forwarded-header` dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`

**Step 1: Add library entry to version catalog**

In `gradle/libs.versions.toml`, add this line after `ktor-server-auth-jvm`:

```toml
ktor-server-forwardedHeader = { module = "io.ktor:ktor-server-forwarded-header", version.ref = "ktor" }
```

**Step 2: Add to `build.gradle.kts` dependencies**

In `build.gradle.kts`, add after `implementation(libs.ktor.server.auth.jvm)`:

```kotlin
implementation(libs.ktor.server.forwardedHeader)
```

**Step 3: Verify the dependency resolves**

```bash
./gradlew dependencies --configuration runtimeClasspath | grep forwarded
```

Expected: a line showing `io.ktor:ktor-server-forwarded-header` resolved to a version.

**Step 4: Verify build still passes**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`

**Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "feat: add ktor-server-forwarded-header dependency"
```

---

### Task 2: Install `XForwardedHeaders` and fix `CallLogging`

**Files:**
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/HTTP.kt`
- Modify: `src/main/kotlin/net/dinomite/ytpodcast/plugins/Monitoring.kt`
- Create: `src/test/kotlin/net/dinomite/ytpodcast/plugins/HTTPTest.kt`

**Step 1: Write failing test**

Create `src/test/kotlin/net/dinomite/ytpodcast/plugins/HTTPTest.kt`:

```kotlin
package net.dinomite.ytpodcast.plugins

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class HTTPTest {
    @Test
    fun `XForwardedHeaders resolves real client IP from X-Forwarded-For header`() = testApplication {
        application {
            configureHTTP()
            routing {
                get("/ip") {
                    call.respondText(call.request.origin.remoteHost)
                }
            }
        }

        client.get("/ip") {
            header("X-Forwarded-For", "1.2.3.4")
        }.apply {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "1.2.3.4"
        }
    }
}
```

**Step 2: Run test to confirm failure**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.plugins.HTTPTest"
```

Expected: FAIL — `XForwardedHeaders` is not installed yet so the IP won't be `1.2.3.4`.

**Step 3: Install `XForwardedHeaders` in `HTTP.kt`**

Add the import and plugin to `src/main/kotlin/net/dinomite/ytpodcast/plugins/HTTP.kt`:

```kotlin
package net.dinomite.ytpodcast.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    install(XForwardedHeaders)

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        anyHost()
    }
}
```

**Step 4: Run test to confirm it passes**

```bash
./gradlew test --tests "net.dinomite.ytpodcast.plugins.HTTPTest"
```

Expected: PASS

**Step 5: Fix `CallLogging` in `Monitoring.kt`**

Replace `src/main/kotlin/net/dinomite/ytpodcast/plugins/Monitoring.kt` with:

```kotlin
package net.dinomite.ytpodcast.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.origin
import io.ktor.server.request.path
import io.ktor.server.response.respond
import net.dinomite.ytpodcast.models.ErrorResponse
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val ip = call.request.origin.remoteHost
            val userAgent = call.request.headers["User-Agent"]
            "$httpMethod ${call.request.path()} - $status - $ip - User-Agent: $userAgent"
        }
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("bad_request", cause.message ?: "Invalid request")
            )
        }

        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", cause.message ?: "Resource not found")
            )
        }

        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "An unexpected error occurred")
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", "The requested resource was not found")
            )
        }
    }
}
```

Two changes from the original:
1. The `filter { call -> call.request.path().startsWith("/api") }` block is removed (it matched nothing — no routes start with `/api`)
2. The format string now includes `val ip = call.request.origin.remoteHost` and adds `$ip` between the status and User-Agent

**Step 6: Run full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

**Step 7: Run ktlint and detekt**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Fix any issues.

**Step 8: Commit**

```bash
git add src/main/kotlin/net/dinomite/ytpodcast/plugins/HTTP.kt \
        src/main/kotlin/net/dinomite/ytpodcast/plugins/Monitoring.kt \
        src/test/kotlin/net/dinomite/ytpodcast/plugins/HTTPTest.kt
git commit -m "feat: log client IP and user-agent via XForwardedHeaders plugin"
```
