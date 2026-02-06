package net.dinomite.ytpodcast.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic
import net.dinomite.ytpodcast.config.AppConfig

fun Application.configureAuthentication(appConfig: AppConfig) {
    install(Authentication) {
        basic("podcast-auth") {
            realm = "YouTube Podcast RSS"
            validate { credentials ->
                if (credentials.name == appConfig.authUsername &&
                    credentials.password == appConfig.authPassword
                ) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}
