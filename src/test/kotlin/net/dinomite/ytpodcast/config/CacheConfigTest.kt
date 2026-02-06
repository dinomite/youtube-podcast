package net.dinomite.ytpodcast.config

import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test

class CacheConfigTest {
    @Test
    fun `CacheConfig holds maxSize`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxSize shouldBe 5368709120L
    }

    @Test
    fun `CacheConfig holds maxCount`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxCount shouldBe 100
    }

    @Test
    fun `CacheConfig holds directory`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.directory shouldBe "/tmp/cache"
    }

    @Test
    fun `CacheConfig allows zero for unlimited size`() {
        val config = CacheConfig(
            maxSize = 0L,
            maxCount = 100,
            directory = "/tmp/cache"
        )

        config.maxSize shouldBe 0L
    }

    @Test
    fun `CacheConfig allows zero for unlimited count`() {
        val config = CacheConfig(
            maxSize = 5368709120L,
            maxCount = 0,
            directory = "/tmp/cache"
        )

        config.maxCount shouldBe 0
    }

    @Test
    fun `loads cache configuration`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.cache.maxSize", "10GB")
            put("ytpodcast.cache.maxCount", "200")
        }

        val cacheConfig = CacheConfig(config, "/tmp")

        cacheConfig.maxSize shouldBe 10737418240L
        cacheConfig.maxCount shouldBe 200
        cacheConfig.directory shouldBe "/tmp"
    }

    @Test
    fun `uses defaults when not in config`() {
        val config = MapApplicationConfig()

        val cacheConfig = CacheConfig(config, "/tmp")

        cacheConfig.maxSize shouldBe 5368709120L // 5GB default
        cacheConfig.maxCount shouldBe 100 // 100 files default
        cacheConfig.directory shouldBe "/tmp"
    }

    @Test
    fun `handles unlimited size`() {
        val config = MapApplicationConfig().apply {
            put("ytpodcast.cache.maxSize", "0")
            put("ytpodcast.cache.maxCount", "50")
        }

        val cacheConfig = CacheConfig(config, "/tmp")

        cacheConfig.maxSize shouldBe 0L
        cacheConfig.maxCount shouldBe 50
    }
}
