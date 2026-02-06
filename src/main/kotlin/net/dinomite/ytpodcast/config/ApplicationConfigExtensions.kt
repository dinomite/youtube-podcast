package net.dinomite.ytpodcast.config

import io.ktor.server.config.ApplicationConfig

fun ApplicationConfig.getStringOrNull(path: String): String? {
    val value = propertyOrNull(path)?.getString()
    return if (value.isNullOrBlank()) null else value
}
