package net.dinomite.ytpodcast.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val baseUrl: String = "",
    val tempDir: String = "",
    val cacheDir: String = "",
    val authUsername: String,
    val authPassword: String,
) {
    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            val baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: ""
            val tempDir = config.propertyOrNull("ytpodcast.tempDir")?.getString()
                ?: "${System.getProperty("java.io.tmpdir")}/tmp"
            val cacheDir = config.propertyOrNull("ytpodcast.cacheDir")?.getString() ?: "$tempDir/cache"

            val authUsername = config.propertyOrNull("ytpodcast.auth.username")?.getString() ?: ""
            val authPassword = config.propertyOrNull("ytpodcast.auth.password")?.getString() ?: ""

            check(authUsername.isNotBlank()) { "ytpodcast.auth.username must be configured" }
            check(authPassword.isNotBlank()) { "ytpodcast.auth.password must be configured" }

            return AppConfig(
                baseUrl = baseUrl,
                tempDir = tempDir,
                cacheDir = cacheDir,
                authUsername = authUsername,
                authPassword = authPassword,
            )
        }
    }
}
