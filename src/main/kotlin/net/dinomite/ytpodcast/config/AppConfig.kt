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
        private val logger = LoggerFactory.getLogger(AppConfig::class.java.name)

        fun load(config: ApplicationConfig): AppConfig {
            val baseUrl = config.getStringOrNull("ytpodcast.baseUrl") ?: ""
            val baseDir = config.getStringOrNull("ytpodcast.baseDir") ?: createBaseDir()

            val authUsername = config.propertyOrNull("ytpodcast.auth.username")?.getString() ?: ""
            val authPassword = config.propertyOrNull("ytpodcast.auth.password")?.getString() ?: ""

            require(authUsername.isNotBlank()) { "ytpodcast.auth.username must be configured" }
            require(authPassword.isNotBlank()) { "ytpodcast.auth.password must be configured" }
            logger.info("Credentials: $authUsername:$authPassword")

            return AppConfig(
                baseUrl = baseUrl,
                tempDir = "$baseDir/tmp",
                cacheDir = "$baseDir/cache",
                authUsername = authUsername,
                authPassword = authPassword,
            )
        }

        private fun createBaseDir(): String = "${System.getProperty("java.io.tmpdir")}/ytpodcast"
    }
}
