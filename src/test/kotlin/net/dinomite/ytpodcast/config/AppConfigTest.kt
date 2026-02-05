package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig
import net.dinomite.ytpodcast.loadAppConfig
import net.dinomite.ytpodcast.loadCacheConfig
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
    fun `loadAppConfig loads tempDir from config`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.tempDir", "/app/audio-cache")
        }

        val appConfig = loadAppConfig(config)

        appConfig.tempDir shouldBe "/app/audio-cache"
    }

    @Test
    fun `loadAppConfig defaults tempDir to system temp when not in config`() {
        val config = MapApplicationConfig()

        val appConfig = loadAppConfig(config)

        appConfig.tempDir shouldBe System.getProperty("java.io.tmpdir")
    }

    @Test
    fun `loadCacheConfig loads cache configuration`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.cache.maxSize", "10GB")
            put("ytpodcast.cache.maxCount", "200")
        }

        val cacheConfig = loadCacheConfig(config, "/tmp")

        cacheConfig.maxSize shouldBe 10737418240L
        cacheConfig.maxCount shouldBe 200
        cacheConfig.directory shouldBe "/tmp"
    }

    @Test
    fun `loadCacheConfig uses defaults when not in config`() {
        val config = MapApplicationConfig()

        val cacheConfig = loadCacheConfig(config, "/tmp")

        cacheConfig.maxSize shouldBe 5368709120L // 5GB default
        cacheConfig.maxCount shouldBe 100 // 100 files default
        cacheConfig.directory shouldBe "/tmp"
    }

    @Test
    fun `loadCacheConfig handles unlimited size`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.cache.maxSize", "0")
            put("ytpodcast.cache.maxCount", "50")
        }

        val cacheConfig = loadCacheConfig(config, "/tmp")

        cacheConfig.maxSize shouldBe 0L
        cacheConfig.maxCount shouldBe 50
    }
}
