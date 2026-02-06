package net.dinomite.ytpodcast.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(val baseUrl: String = "", val tempDir: String = "",) {
    constructor(config: ApplicationConfig) : this(
        baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: "",
        tempDir = config.propertyOrNull("ytpodcast.tempDir")?.getString() ?: System.getProperty("java.io.tmpdir"),
    )
}
