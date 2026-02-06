package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppConfigTest {
    @Test
    fun `load reads auth credentials from config`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.auth.username", "testuser")
            put("ytpodcast.auth.password", "testpass")
        }

        val appConfig = AppConfig.load(config)

        appConfig.authUsername shouldBe "testuser"
        appConfig.authPassword shouldBe "testpass"
    }

    @Test
    fun `load throws exception when username is missing`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.auth.password", "testpass")
        }

        assertThrows<IllegalArgumentException> {
            AppConfig.load(config)
        }
    }

    @Test
    fun `load throws exception when password is missing`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.auth.username", "testuser")
        }

        assertThrows<IllegalArgumentException> {
            AppConfig.load(config)
        }
    }

    @Test
    fun `load throws exception when username is empty`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.auth.username", "")
            put("ytpodcast.auth.password", "testpass")
        }

        assertThrows<IllegalArgumentException> {
            AppConfig.load(config)
        }
    }

    @Test
    fun `load throws exception when password is empty`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.auth.username", "testuser")
            put("ytpodcast.auth.password", "")
        }

        assertThrows<IllegalArgumentException> {
            AppConfig.load(config)
        }
    }

    @Test
    fun `tempDir defaults when config value is empty string`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.tempDir", "")
            put("ytpodcast.auth.username", "testuser")
            put("ytpodcast.auth.password", "testpass")
        }

        val appConfig = AppConfig.load(config)

        appConfig.tempDir shouldBe "${System.getProperty("java.io.tmpdir")}/tmp"
    }

    @Test
    fun `cacheDir defaults to tempDir-based path when config value is empty`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.tempDir", "/custom/tmp")
            put("ytpodcast.auth.username", "testuser")
            put("ytpodcast.auth.password", "testpass")
        }

        val appConfig = AppConfig.load(config)

        appConfig.cacheDir shouldBe "/custom/tmp/cache"
    }

    @Test
    fun `tempDir uses configured value when present`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.tempDir", "/custom/tmp")
            put("ytpodcast.auth.username", "testuser")
            put("ytpodcast.auth.password", "testpass")
        }

        val appConfig = AppConfig.load(config)

        appConfig.tempDir shouldBe "/custom/tmp"
    }
}
