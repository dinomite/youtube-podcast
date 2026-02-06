package net.dinomite.ytpodcast.config

import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory

data class AppConfig(
    val baseUrl: String = "",
    val tempDir: String = "",
    val cacheDir: String = "",
    val authUsername: String,
    val authPassword: String,
) {
    companion object {
        private val logger = LoggerFactory.getLogger("AppConfig")

        fun load(config: ApplicationConfig): AppConfig {
            val baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: ""
            val tempDir = config.propertyOrNull("ytpodcast.tempDir")?.getString()
                ?: "${System.getProperty("java.io.tmpdir")}/tmp"
            val cacheDir = config.propertyOrNull("ytpodcast.cacheDir")?.getString() ?: "$tempDir/cache"

            val authUsername = config.propertyOrNull("ytpodcast.auth.username")?.getString() ?: ""
            val authPassword = config.propertyOrNull("ytpodcast.auth.password")?.getString() ?: ""

            require(authUsername.isNotBlank()) { "ytpodcast.auth.username must be configured" }
            require(authPassword.isNotBlank()) { "ytpodcast.auth.password must be configured" }
            logger.info("Username $authUsername, Password $authPassword")

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
