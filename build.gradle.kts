plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)

    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "net.dinomite"
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
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.ktor.server.forwardedHeader)

    // Logging
    implementation(libs.klogging)
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.bundles.testing)

    // Ktor Client (for testing)
    testImplementation(libs.bundles.ktor.client)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("net.dinomite.ytpodcast.ApplicationKt")
}

ktlint {
    // Should match IntelliJ ktlint plugin version https://github.com/nbadal/ktlint-intellij-plugin
    version.set("1.8.0")
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
