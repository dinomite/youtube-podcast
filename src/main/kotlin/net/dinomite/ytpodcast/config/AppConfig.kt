package net.dinomite.ytpodcast.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(val baseUrl: String = "", val tempDir: String = "", val cacheDir: String = "") {
    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            val baseUrl = config.propertyOrNull("ytpodcast.baseUrl")?.getString() ?: ""
            val tempDir = config.propertyOrNull("ytpodcast.tempDir")?.getString()
                ?: "${System.getProperty("java.io.tmpdir")}/tmp"
            val cacheDir = config.propertyOrNull("ytpodcast.cacheDir")?.getString() ?: "$tempDir/cache"

            return AppConfig(
                baseUrl = baseUrl,
                tempDir = tempDir,
                cacheDir = cacheDir,
            )
        }
    }
}
