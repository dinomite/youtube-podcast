plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)

    kotlin("plugin.serialization") version "1.9.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.defaultHeaders)
    implementation(libs.ktor.server.cors)

    // Logging
    implementation(libs.logback.classic)

    // YouTube library
    implementation(libs.youtube.downloader)

    // JAVE - Multimedia transcoding (using the bundle)
    implementation(libs.bundles.jave)

    // XML/RSS generation
    implementation(libs.rome)

    // Logging (no change needed)
    implementation(libs.logback.classic)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Caching
    implementation(libs.caffeine)

    // Testing (using the bundle)
    testImplementation(libs.bundles.testing)

    // Ktor Client (for testing)
    testImplementation(libs.bundles.ktor.client)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.example.ytpodcast.ApplicationKt")
}

ktlint {
    // Should match IntelliJ ktlint plugin version https://github.com/nbadal/ktlint-intellij-plugin
    version.set("1.7.1")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/detekt.yml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html { require(true) }
    }
}
