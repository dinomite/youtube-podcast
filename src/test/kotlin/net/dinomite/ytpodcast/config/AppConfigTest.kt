package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test

class AppConfigTest {
    @Test
    fun `AppConfig holds baseUrl`() {
        val config = AppConfig(baseUrl = "http://example.com", tempDir = "/tmp")

        config.baseUrl shouldBe "http://example.com"
    }

    @Test
    fun `AppConfig holds tempDir`() {
        val config = AppConfig(baseUrl = "", tempDir = "/custom/temp")

        config.tempDir shouldBe "/custom/temp"
    }

    @Test
    fun `AppConfig allows empty tempDir`() {
        val config = AppConfig(baseUrl = "", tempDir = "")

        config.tempDir shouldBe ""
    }

    @Test
    fun `loads tempDir from config`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.tempDir", "/app/audio-cache")
        }

        val appConfig = AppConfig.load(config)

        appConfig.tempDir shouldBe "/app/audio-cache"
    }

    @Test
    fun `defaults tempDir to system temp when not in config`() {
        val config = MapApplicationConfig()

        val appConfig = AppConfig.load(config)

        appConfig.tempDir shouldBe "${System.getProperty("java.io.tmpdir")}/tmp"
    }
}
